package com.min.edu.booth.service;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.booth.domain.BoothQrScan;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothCheckInService {

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final BoothReservationRepository reservationRepository;
    private final BoothQrScanRepository qrScanRepository;

    /**
     * 부스 방문 확인 (checkIn)
     * 1. 교환 코드 검증
     * 2. 예약자 확인
     * 3. 상태 변경: RESERVED → CHECKED_IN
     * 4. BoothQrScan 기록 저장 (혼잡도 계산용)
     */
    public BoothReservationResponse checkIn(
            Long boothId,
            String exchangeCode,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 1️⃣ 교환 코드 조회 (비관적 잠금)
        ExchangeCode code = exchangeCodeRepository.findByCodeForUpdate(exchangeCode)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2️⃣ 교환 코드 검증
        if (code.isRedeemed()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 이미 사용됨
        }
        if (code.isCancelled()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 취소됨
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(now)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);  // 만료됨
        }

        // 3️⃣ 예약 조회 (비관적 잠금)
        BoothReservation reservation = reservationRepository.findByMemberIdAndBoothIdWithLock(memberId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 4️⃣ 예약 상태 확인 (RESERVED만 입장 가능)
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 5️⃣ 상태 변경: RESERVED → CHECKED_IN
        reservation.updateStatus(BoothReservationStatus.CHECKED_IN);
        reservation.updateCheckedInAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 6️⃣ 교환 코드 상태 변경: ISSUED → REDEEMED
        code.redeem(now);
        exchangeCodeRepository.saveAndFlush(code);

        // 7️⃣ BoothQrScan 기록 저장 (혼잡도 계산용)
        boolean isDuplicate = checkDuplicateScan(code.getId(), boothId);
        BoothQrScan qrScan = BoothQrScan.builder()
                .boothId(boothId)
                .exchangeCodeId(code.getId())
                .scannedAt(now)
                .duplicate(isDuplicate)
                .build();
        qrScanRepository.saveAndFlush(qrScan);

        return toResponse(reservation);
    }

    /**
     * 중복 스캔 여부 판별
     * (같은 교환 코드가 이미 스캔됐으면 true)
     */
    private boolean checkDuplicateScan(Long exchangeCodeId, Long boothId) {
        return qrScanRepository.existsByExchangeCodeId(exchangeCodeId);
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
