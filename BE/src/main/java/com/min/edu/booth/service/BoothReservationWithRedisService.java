package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationWithRedisService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;
    private final BoothRepository boothRepository;
    private final RedisReservationService redisReservationService;

    /**
     * WBS-146: Redis 선점을 포함한 예약 생성 (통합 서비스)
     *
     * Redis에 임시 선점 시도
     * 선점 성공 → DB에 예약 저장
     * DB 커밋 후 Redis 선점 해제
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

        // 슬롯 확인
        BoothReservationSlot slot = slotRepository.findById(request.getSlotId())
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
                    .reservedAt(now)
                    .updatedAt(now)
                    .build();

            BoothReservation saved = reservationRepository.saveAndFlush(reservation);

            // 슬롯의 남은 자리 감소
            slot.incrementReservedCount(request.getPartySize());
            slotRepository.saveAndFlush(slot);

            //  DB 트랜잭션 성공 후 Redis 선점 해제
            // (임시 선점 키가 남지 않음)
            redisReservationService.releaseSlot(boothId, request.getSlotId(),memberId);

            return toResponse(saved);

        } catch (Exception e) {
            // 예약 생성 실패 시 Redis 선점 해제
            redisReservationService.releaseSlot(boothId, request.getSlotId(),memberId);
            throw e;
        }
    }

    /**
     * 예약 취소
     */
    @Transactional
    public void cancelReservation(Long reservationId, Long memberId) {
        BoothReservation reservation = reservationRepository.findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 상태 변경
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 슬롯 자리 복원
        BoothReservationSlot slot = slotRepository.findById(reservation.getBoothReservationSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // Redis 선점 해제
        redisReservationService.releaseSlot(reservation.getBoothId(), reservation.getBoothReservationSlotId(),memberId);
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