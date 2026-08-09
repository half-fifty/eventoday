package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
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

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;
    private final BoothRepository boothRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 예약 생성 (WBS-146: Redis 선점 포함)
     * - 부스/슬롯 검증
     * - 예약 저장
     * - 슬롯 인원 증가
     */
    @Transactional
    public BoothReservationResponse createReservation(
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
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 5️⃣ 중복 예약 확인
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 6️⃣ DB에 예약 저장
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

        // 7️⃣ 슬롯의 예약된 인원 증가
        slot.incrementReservedCount(request.getPartySize());
        slotRepository.saveAndFlush(slot);

        return toResponse(saved);
    }

    /**
     * 예약 취소 (Event 패턴 - 비동기 알림)
     * - 예약 상태 변경
     * - 슬롯 자리 복원
     * - 빈자리 알림 이벤트 발행 (커밋 후 비동기)
     */
    @Transactional
    public void cancelReservation(Long reservationId, Long boothId, Long memberId) {
        BoothReservation reservation = reservationRepository.findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1️⃣ 예약 상태 변경 (트랜잭션 내)
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 2️⃣ 슬롯 자리 복원 (트랜잭션 내)
        BoothReservationSlot slot = slotRepository.findById(reservation.getBoothReservationSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // 3️⃣ 부스 정보 조회 (부스명 전달용)
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 4️⃣ 빈자리 알림 이벤트 발행 (커밋 후 비동기)
        // 생성자 순서: source, boothId, slotId, idempotencyKey, displayName
        eventPublisher.publishEvent(new BoothVacancyEvent(
                this,
                boothId,
                reservation.getBoothReservationSlotId(),  // slotId (Long)
                reservationId,                             // idempotencyKey (Long)
                booth.getDisplayName()                     // displayName (String)
        ));
    }

    /**
     * BoothReservation → BoothReservationResponse 변환
     */
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