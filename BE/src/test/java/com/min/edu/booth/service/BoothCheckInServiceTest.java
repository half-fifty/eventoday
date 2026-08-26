package com.min.edu.booth.service;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothCheckInResponse;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoothCheckInServiceTest {

    private static final Long BOOTH_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long MEMBER_ID = 100L;
    private static final Long TICKET_ID = 200L;
    private static final Long EXCHANGE_CODE_ID = 300L;
    private static final String QR_TOKEN = "booth-qr-token";

    @Mock private AdmissionTicketRepository admissionTicketRepository;
    @Mock private ExchangeCodeRepository exchangeCodeRepository;
    @Mock private BoothReservationRepository reservationRepository;
    @Mock private BoothQrScanRepository qrScanRepository;
    @Mock private BoothRepository boothRepository;

    @InjectMocks
    private BoothCheckInService service;

    private Booth booth() {
        return Booth.builder().id(BOOTH_ID).eventId(EVENT_ID).qrToken(QR_TOKEN).build();
    }

    private ExchangeCode exchangeCode(Long eventId) {
        return ExchangeCode.builder()
            .id(EXCHANGE_CODE_ID)
            .eventId(eventId)
            .status(ExchangeCodeStatus.ISSUED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private AdmissionTicket ticket(AdmissionTicketStatus status, Long memberId) {
        return AdmissionTicket.builder()
            .id(TICKET_ID)
            .exchangeCodeId(EXCHANGE_CODE_ID)
            .memberId(memberId)
            .qrToken("token-abc")
            .status(status)
            .issuedAt(OffsetDateTime.now())
            .build();
    }

    @Test
    void 게이트에서_이미_입장처리된_본인_티켓이면_예약이_없어도_체크인에_성공한다() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, MEMBER_ID)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));
        given(reservationRepository.findByMemberIdAndBoothIdWithLock(MEMBER_ID, BOOTH_ID)).willReturn(Optional.empty());

        BoothCheckInResponse response = service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID);

        assertThat(response.getBoothId()).isEqualTo(BOOTH_ID);
        assertThat(response.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(response.isReservationLinked()).isFalse();
        assertThat(response.getReservationStatus()).isNull();
        verify(qrScanRepository).saveAndFlush(any());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 슬롯_예약이_있으면_체크인_시_RESERVED에서_CHECKED_IN으로_함께_전환한다() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, MEMBER_ID)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));

        BoothReservation reservation = BoothReservation.builder()
            .boothId(BOOTH_ID)
            .memberId(MEMBER_ID)
            .partySize(2)
            .status(BoothReservationStatus.RESERVED)
            .createdAt(OffsetDateTime.now())
            .reservedAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        given(reservationRepository.findByMemberIdAndBoothIdWithLock(MEMBER_ID, BOOTH_ID)).willReturn(Optional.of(reservation));

        BoothCheckInResponse response = service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID);

        assertThat(response.isReservationLinked()).isTrue();
        assertThat(response.getReservationStatus()).isEqualTo(BoothReservationStatus.CHECKED_IN);
        assertThat(reservation.getStatus()).isEqualTo(BoothReservationStatus.CHECKED_IN);
        assertThat(reservation.getCheckedInAt()).isNotNull();
        verify(reservationRepository).saveAndFlush(reservation);
    }

    @Test
    void 부스에_아직_QR이_발급되지_않았으면_예외() {
        given(boothRepository.findById(BOOTH_ID))
            .willReturn(Optional.of(Booth.builder().id(BOOTH_ID).eventId(EVENT_ID).qrToken(null).build()));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_CHECK_IN_QR_INVALID);
        verify(admissionTicketRepository, never()).findById(any());
    }

    @Test
    void QR_토큰이_일치하지_않으면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, "다른-토큰", MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_CHECK_IN_QR_INVALID);
        verify(admissionTicketRepository, never()).findById(any());
    }

    @Test
    void 존재하지_않는_티켓이면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);
    }

    @Test
    void 다른_행사의_입장권이면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, MEMBER_ID)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(999L)));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        verify(qrScanRepository, never()).saveAndFlush(any());
    }

    @Test
    void 본인_명의가_아닌_입장권이면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, 999L)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void 소유자가_없는_입장권이면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, null)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
        verify(qrScanRepository, never()).saveAndFlush(any());
    }

    @Test
    void 게이트에서_아직_입장처리되지_않은_티켓이면_예외() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.ISSUED, MEMBER_ID)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_CHECK_IN_NOT_ADMITTED);
        verify(qrScanRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_부스에_이미_체크인한_적이_있으면_예외로_변환된다() {
        given(boothRepository.findById(BOOTH_ID)).willReturn(Optional.of(booth()));
        given(admissionTicketRepository.findById(TICKET_ID)).willReturn(Optional.of(ticket(AdmissionTicketStatus.USED, MEMBER_ID)));
        given(exchangeCodeRepository.findById(EXCHANGE_CODE_ID)).willReturn(Optional.of(exchangeCode(EVENT_ID)));
        given(qrScanRepository.saveAndFlush(any()))
            .willThrow(new DataIntegrityViolationException("uk_booth_qr_scans_booth_ticket"));

        assertThatThrownBy(() -> service.checkIn(BOOTH_ID, TICKET_ID, QR_TOKEN, MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_CHECK_IN_ALREADY_DONE);
        verify(reservationRepository, never()).findByMemberIdAndBoothIdWithLock(any(), any());
    }
}
