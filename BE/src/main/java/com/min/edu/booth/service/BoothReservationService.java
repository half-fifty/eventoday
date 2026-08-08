package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final BoothVacancyNotificationService vacancyNotificationService;


    // BoothReservationSlot에서 이미 부스 검증하므로 여기서는 필요 없음
    // 예약 확정
    public BoothReservationResponse createReservation(
            Long boothId,
            CreateBoothReservationRequest request,
            Long memberId) {

        // 1. 슬롯 조회 (락 걸음)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2. 부스 ID 일치 검증
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 3. 슬롯 상태 검증 (CLOSED와 모든 non-OPEN 상태 거부)
        if (slot.getStatus() != BoothReservationSlotStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. 중복 예약 확인
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 5. 정원 확인
        int remainingCapacity = slot.getCapacity() - slot.getReservedCount();
        if (request.getPartySize() > remainingCapacity) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 6. 예약 생성
        BoothReservation reservation = BoothReservation.builder()
                .boothId(boothId)
                .memberId(memberId)
                .boothReservationSlotId(slot.getId())
                .partySize(request.getPartySize())
                .status(BoothReservationStatus.RESERVED)
                .reservedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservation saved = reservationRepository.saveAndFlush(reservation);

        // 7. 슬롯 업데이트
        slot.incrementReservedCount(request.getPartySize());
        slotRepository.saveAndFlush(slot);

        return toResponse(saved);
    }
    // 예약 취소
    @Transactional
    public void cancelReservation(Long reservationId, Long boothId, Long memberId) {

        // 1. 예약 조회 (비관적 잠금)
        BoothReservation reservation = reservationRepository.findByIdAndMemberIdWithLock(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2. boothId 검증
        if (!reservation.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 3. 취소 가능 상태 확인
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. 원자적 상태 변경 (한 요청만 진행)
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(OffsetDateTime.now());
        reservation.updateUpdatedAt(OffsetDateTime.now());
        reservationRepository.saveAndFlush(reservation);  // flush로 즉시 반영

        // 5. 슬롯 복원 (예약 상태 변경 후 실행)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                .orElseThrow();

        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // 6️.빈자리 알림 발송
        boothRepository.findById(boothId).ifPresent(booth ->
                vacancyNotificationService.notifyVacancy(boothId, booth.getDisplayName())
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

