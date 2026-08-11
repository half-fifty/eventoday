package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.event.BoothVacancyEvent;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationWithRedisService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;
    private final BoothRepository boothRepository;
    private final RedisReservationService redisReservationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * WBS-146: Redis 선점을 포함한 예약 생성 (통합 서비스)
     *
     * Redis에 임시 선점 시도
     * 선점 성공 → DB에 예약 저장
     * ⭐ DB 트랜잭션 커밋 후 afterCompletion에서 Redis 선점 해제
     *
     * 예외 발생 시: try-catch에서 지금 바로 해제 (트랜잭션 롤백 전)
     *
     * Controller에서는 이 메서드만 호출하면 됨 (Redis 처리 X)
     */
    @Transactional
    public BoothReservationResponse createReservationWithRedis(
            Long boothId,
            CreateBoothReservationRequest request,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 부스 존재 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 슬롯 확인 (비관적 잠금으로 동시성 제어)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 슬롯의 부스가 맞는지 확인 (booth ownership)
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 슬롯 상태 확인 (OPEN이어야 함)
        if (slot.getStatus() != BoothReservationSlotStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // partySize가 남은 자리를 초과하는지 확인
        int remainingCapacity = slot.getCapacity() - slot.getReservedCount();
        if (request.getPartySize() > remainingCapacity) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 중복 예약 확인
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // Redis에 임시 선점 시도 (WBS-146)
        boolean reserved = redisReservationService.reserveSlot(boothId, request.getSlotId(), memberId);
        if (!reserved) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 이미 다른 사용자가 선점
        }

        try {
            // DB에 예약 저장
            BoothReservation reservation = BoothReservation.builder()
                    .boothId(boothId)
                    .boothReservationSlotId(request.getSlotId())
                    .memberId(memberId)
                    .partySize(request.getPartySize())
                    .status(BoothReservationStatus.RESERVED)
                    .createdAt(now)
                    .reservedAt(now)
                    .updatedAt(now)
                    .build();

            BoothReservation saved = reservationRepository.saveAndFlush(reservation);

            // 슬롯의 남은 자리 감소 (이미 비관적 잠금으로 보호됨)
            slot.incrementReservedCount(request.getPartySize());
            slotRepository.saveAndFlush(slot);

            // ⭐ DB 트랜잭션 커밋 후 Redis 선점 해제 등록
            // 성공·실패 모두 afterCompletion에서 한 번만 수행
            registerRedisReleaseOnCommit(boothId, request.getSlotId(), memberId);

            return toResponse(saved);

        } catch (Exception e) {
            // ⚠️ 예약 생성 실패 시 (트랜잭션 커밋 전 실패)
            // 지금 바로 Redis 선점 해제 (트랜잭션 롤백 전)
            redisReservationService.releaseSlot(boothId, request.getSlotId(), memberId);
            throw e;
        }
    }

    /**
     * 예약 취소 (이벤트 발행 포함)
     * - 예약 상태 변경 (RESERVED → CANCELLED)
     * - 슬롯 자리 복원
     * - 빈자리 알림 이벤트 발행
     * - ⭐ 트랜잭션 커밋 후 afterCompletion에서 Redis 선점 해제
     */
    @Transactional
    public void cancelReservation(Long reservationId, Long boothId, Long memberId) {
        // 1️⃣ 예약 조회 (비관적 잠금)
        BoothReservation reservation = reservationRepository.findByIdAndMemberIdWithLock(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2boothId 검증
        if (!reservation.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 예약 상태 확인
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 예약 상태 변경 (CANCELLED)
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 5️⃣ 슬롯 자리 복원 (비관적 잠금)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // ⭐ lower-bound 검증: 예약 인원 수보다 현재 예약 수가 작으면 에러
        if (slot.getReservedCount() < reservation.getPartySize()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // 6️⃣ ⭐ 트랜잭션 커밋 후 Redis 선점 해제 등록
        // 성공·실패 모두 afterCompletion에서 한 번만 수행
        registerRedisReleaseOnCommit(boothId, reservation.getBoothReservationSlotId(), memberId);

        // 7️⃣ ⭐ 빈자리 알림 이벤트 발행
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        eventPublisher.publishEvent(new BoothVacancyEvent(
                this,
                boothId,
                reservation.getBoothReservationSlotId(),
                reservationId,
                booth.getDisplayName()
        ));
    }

    /**
     * ⭐ TransactionSynchronizationManager를 사용한 Redis 해제 등록
     *
     * DB 트랜잭션 커밋 이후 Redis 선점 해제
     * 성공·실패 모두 afterCompletion에서 한 번만 수행
     *
     * - 커밋 성공: afterCompletion 실행 → Redis 해제
     * - 롤백 실패: afterCompletion 실행 → Redis 해제
     * 결과: 중복 해제 절대 불가능!
     */
    private void registerRedisReleaseOnCommit(Long boothId, Long slotId, Long memberId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        // status: STATUS_COMMITTED (성공) 또는 STATUS_ROLLED_BACK (실패)
                        // 둘 다 여기서 한 번만 실행됨
                        redisReservationService.releaseSlot(boothId, slotId, memberId);
                    }
                }
        );
    }

    private BoothReservationResponse toResponse(BoothReservation reservation) {
        return BoothReservationResponse.builder()
                .id(reservation.getId())
                .boothId(reservation.getBoothId())
                .slotId(reservation.getBoothReservationSlotId())
                .memberId(reservation.getMemberId())
                .partySize(reservation.getPartySize())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .cancelledAt(reservation.getCancelledAt())
                .checkedInAt(reservation.getCheckedInAt())
                .noShowAt(reservation.getNoShowAt())
                .build();
    }
}