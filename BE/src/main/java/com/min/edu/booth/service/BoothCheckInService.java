package com.min.edu.booth.service;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothQrScan;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothRepository;
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
    private final BoothRepository boothRepository;

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

        // 부스의 eventId 먼저 조회
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 교환 코드 조회
        ExchangeCode code = exchangeCodeRepository.findByCodeForUpdate(exchangeCode)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 교환 코드의 eventId와 부스의 eventId 일치 확인
        if (!code.getEventId().equals(booth.getEventId())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 홀더 멤버ID 확인 (본인 코드인지)
        if (!code.getHolderMemberId().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 교환 코드 검증
        if (code.isRedeemed()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        if (code.isCancelled()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(now)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 예약 조회 (비관적 잠금)
        BoothReservation reservation = reservationRepository.findByMemberIdAndBoothIdWithLock(memberId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 예약 상태 확인 (RESERVED만 입장 가능)
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 상태 변경: RESERVED → CHECKED_IN
        reservation.updateStatus(BoothReservationStatus.CHECKED_IN);
        reservation.updateCheckedInAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 교환 코드 상태 변경: ISSUED → REDEEMED
        code.redeem(now);
        exchangeCodeRepository.saveAndFlush(code);

        // ⭐ BoothQrScan 기록 저장 (혼잡도 계산용)
        // 같은 booth에서의 최근 rescan 여부 확인 (booth 기준)
        boolean isDuplicate = checkDuplicateScan(code.getId(), boothId);
        BoothQrScan qrScan = BoothQrScan.builder()
                .boothId(boothId)
                .exchangeCodeId(code.getId())
                .scannedAt(now)
                .duplicate(isDuplicate)  // ← 정확한 중복 여부 반영
                .build();
        qrScanRepository.saveAndFlush(qrScan);

        return toResponse(reservation);
    }

    /**
     * 중복 스캔 여부 판별 (booth 기준)
     *
     * 같은 부스에서 같은 교환 코드로 이미 스캔되었으면 true
     * → 여러 부스에서 스캔되면 각각 unique로 취급 (부스별 혼잡도 정확화)
     *
     * @param exchangeCodeId 교환 코드 ID
     * @param boothId 부스 ID
     * @return 같은 booth에서의 최근 rescan이면 true
     */
    private boolean checkDuplicateScan(Long exchangeCodeId, Long boothId) {
        // ⭐ booth + exchangeCode 모두 고려 (변경됨!)
        return qrScanRepository.existsByBoothIdAndExchangeCodeId(boothId, exchangeCodeId);
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