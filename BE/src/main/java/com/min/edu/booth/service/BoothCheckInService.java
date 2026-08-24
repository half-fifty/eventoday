package com.min.edu.booth.service;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothQrScan;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothCheckInResponse;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothCheckInService {

    private final AdmissionTicketRepository admissionTicketRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final BoothReservationRepository reservationRepository;
    private final BoothQrScanRepository qrScanRepository;
    private final BoothRepository boothRepository;

    /**
     * 부스 체크인 (입장 QR 인식)
     *
     * AdmissionTicket(게이트에서 이미 입장 처리된 "입장권")으로 방문객을 식별한다. 예전엔
     * ExchangeCode(구매 수량당 1개, 평생 1회만 사용 가능한 교환코드)를 그대로 재사용해서 첫
     * 부스에서 체크인하는 순간 코드가 영구 소모돼 다른 부스에서는 다시 쓸 수 없었다.
     * AdmissionTicket은 게이트 입장 후에도 소모되지 않으므로 부스마다 반복해서 인식할 수 있다 —
     * "부스당 한 번만 체크인 가능"은 booth_qr_scans의 (booth_id, admission_ticket_id) 유니크
     * 제약으로 별도 보장한다.
     *
     * 방문객은 부스 현장에 게시된 QR(부스 상세 페이지 링크에 booth.qrToken이 실려있음)을 스캔해
     * 이 체크인 화면에 도달한다. FE가 그 qrToken을 그대로 실어 보내면, 서버는 이 부스에 실제로
     * 발급된 토큰과 일치하는지 대조한다 — boothId만 알고 API를 직접 호출하는 것으로는(그 부스
     * QR을 실제로 스캔하지 않는 한) 체크인이 성립하지 않게 막기 위함이다.
     */
    public BoothCheckInResponse checkIn(Long boothId, Long admissionTicketId, String qrToken, Long memberId) {
        OffsetDateTime now = OffsetDateTime.now();

        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND));

        // 이 부스에 발급된 QR과 일치하는지 확인. 아직 운영자가 QR을 발급하지 않았으면
        // booth.getQrToken()이 null이라 그 무엇과도 일치할 수 없다.
        if (booth.getQrToken() == null || !booth.getQrToken().equals(qrToken)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CHECK_IN_QR_INVALID);
        }

        AdmissionTicket ticket = admissionTicketRepository.findById(admissionTicketId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));

        // 이 QR이 이 부스가 속한 행사의 입장권이 맞는지 확인
        ExchangeCode exchangeCode = exchangeCodeRepository.findById(ticket.getExchangeCodeId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        if (!exchangeCode.getEventId().equals(booth.getEventId())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 본인 명의 입장권인지 확인 - 소유자가 없는(게스트) 입장권은 이 인증 기반 API로는
        // 누구 것인지 증명할 방법이 없으므로 무조건 거부한다. null을 통과시키면 ID만 알면
        // 아무 로그인 사용자나 그 게스트 티켓으로 체크인할 수 있게 된다.
        if (!Objects.equals(ticket.getMemberId(), memberId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 게이트에서 이미 입장 처리(USED)된 티켓만 부스 체크인 가능 — 아직 행사장에 입장도
        // 안 한 상태에서 부스부터 체크인할 수는 없다.
        if (ticket.getStatus() != AdmissionTicketStatus.USED) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CHECK_IN_NOT_ADMITTED);
        }

        // 부스 QR 스캔 기록 저장 (혼잡도 계산용). 유니크 제약(booth_id, admission_ticket_id)이
        // 같은 부스에서의 재스캔을 막아준다 — 사전 exists 체크 없이 바로 저장을 시도하고,
        // 제약 위반을 명확한 비즈니스 에러로 변환한다(신고/도움이돼요와 동일한 패턴).
        BoothQrScan scan = BoothQrScan.builder()
                .boothId(boothId)
                .admissionTicketId(ticket.getId())
                .scannedAt(now)
                .build();
        try {
            qrScanRepository.saveAndFlush(scan);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CHECK_IN_ALREADY_DONE, e);
        }

        // 이 부스에 슬롯 예약이 있었다면(RESERVED) 그 예약도 CHECKED_IN으로 함께 반영한다.
        // 예약 없이 QR만 찍는 워크인 방문이면 예약 갱신 없이 스캔 기록만 남긴다.
        BoothReservation reservation = reservationRepository
                .findByMemberIdAndBoothIdWithLock(memberId, boothId)
                .orElse(null);
        if (reservation != null && reservation.getStatus() == BoothReservationStatus.RESERVED) {
            reservation.updateStatus(BoothReservationStatus.CHECKED_IN);
            reservation.updateCheckedInAt(now);
            reservation.updateUpdatedAt(now);
            reservationRepository.saveAndFlush(reservation);
        }

        return BoothCheckInResponse.builder()
                .boothId(boothId)
                .memberId(memberId)
                .checkedInAt(now)
                .reservationLinked(reservation != null)
                .reservationStatus(reservation != null ? reservation.getStatus() : null)
                .build();
    }
}
