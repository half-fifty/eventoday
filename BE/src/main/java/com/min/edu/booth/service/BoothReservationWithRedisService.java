package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
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
     * WBS-146: Redis 선점을 포함한 예약 생성
     *
     * 1. Redis에 임시 선점 시도
     * 2. 선점 성공 → DB에 예약 저장
     * 3. 선점 실패 → 예외 발생
     */
    public BoothReservationResponse createReservationWithRedis(
            Long boothId,
            CreateBoothReservationRequest request,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 1️⃣ 부스 존재 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2️⃣ 슬롯 확인
        BoothReservationSlot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 3️⃣ 슬롯의 부스가 맞는지 확인
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 4️⃣ 슬롯에 빈자리가 있는지 확인
        if (slot.getReservedCount() >= slot.getCapacity()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 자리 없음
        }

        // 5️⃣ 중복 예약 확인 (같은 사용자가 이미 이 부스를 예약했나?)
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 중복 예약
        }

        // 6️⃣ Redis에 임시 선점 시도 (WBS-146)
        boolean reserved = redisReservationService.reserveSlot(boothId, request.getSlotId(), memberId);
        if (!reserved) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 이미 다른 사용자가 선점
        }

        try {
            // 7️⃣ DB에 예약 저장
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

            // 8️⃣ 슬롯의 남은 자리 감소
            slot.incrementReservedCount(request.getPartySize());
            slotRepository.saveAndFlush(slot);

            return toResponse(saved);

        } catch (Exception e) {
            // 예약 생성 실패 시 Redis 선점 해제
            redisReservationService.releaseSlot(boothId, request.getSlotId());
            throw e;
        }
    }

    /**
     * 예약 취소 (Redis 선점 해제)
     */
    public void cancelReservation(Long reservationId, Long memberId) {
        BoothReservation reservation = reservationRepository.findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1️⃣ 상태 변경
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 2️⃣ 슬롯 자리 복원
        BoothReservationSlot slot = slotRepository.findById(reservation.getBoothReservationSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // 3️⃣ Redis 선점 해제 (WBS-146)
        redisReservationService.releaseSlot(reservation.getBoothId(), reservation.getBoothReservationSlotId());
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