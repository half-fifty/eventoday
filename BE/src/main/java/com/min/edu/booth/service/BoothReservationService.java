package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.exception.BoothErrorCode;
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
public class BoothReservationService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;
    // BoothReservationSlot에서 이미 부스 검증하므로 여기서는 필요 없음
    // 예약 확정
    public BoothReservationResponse createReservation(
            Long boothId,
            CreateBoothReservationRequest request,
            Long memberId) {

        // 1. 슬롯 조회 (락 걸음)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("시간대를 찾을 수 없습니다"));

        // 2. 부스 ID 일치 검증
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 3. 이미 예약했는지 확인
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. 정원 확인
        if (slot.getReservedCount() + request.getPartySize() > slot.getCapacity()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 5. 예약 생성
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

        // 6. 슬롯 업데이트
        slot.incrementReservedCount(request.getPartySize());
        slotRepository.saveAndFlush(slot);

        return toResponse(saved);
    }

    // 예약 취소
    public void cancelReservation(Long reservationId, Long memberId) {

        // 1. 예약 조회
        BoothReservation reservation = reservationRepository.findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2. 취소 가능 상태 확인
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 3. 예약 상태 변경
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(OffsetDateTime.now());
        reservation.updateUpdatedAt(OffsetDateTime.now());
        reservationRepository.saveAndFlush(reservation);

        // 4. 슬롯 복원 (락 걸음)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                .orElseThrow();

        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);
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

