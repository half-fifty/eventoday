package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.policy.AdmissionEligibilityPolicy;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionEligibilityQueryServiceTest {

    private final AdmissionTicketRepository admissionTicketRepository =
        Mockito.mock(AdmissionTicketRepository.class);
    private final ExchangeCodeRepository exchangeCodeRepository =
        Mockito.mock(ExchangeCodeRepository.class);
    private final EventRepository eventRepository = Mockito.mock(EventRepository.class);
    private final AdmissionEligibilityPolicy admissionEligibilityPolicy =
        Mockito.mock(AdmissionEligibilityPolicy.class);
    private final AdmissionEligibilityQueryService service = new AdmissionEligibilityQueryService(
        admissionTicketRepository,
        exchangeCodeRepository,
        eventRepository,
        admissionEligibilityPolicy
    );

    @Test
    void evaluatesAdmissionTicketOnlyInsideRequestedEvent() {
        AdmissionTicket ticket = ticket();
        ExchangeCode exchangeCode = exchangeCode(100L);
        Event event = event(100L);
        AdmissionEligibilityResult expected = new AdmissionEligibilityResult(
            true,
            AdmissionEligibilityReasonCode.ELIGIBLE,
            AdmissionTicketStatus.ISSUED,
            EventStatus.PUBLISHED,
            event.getEndAt(),
            null
        );
        given(admissionTicketRepository.findByIdAndEventId(11L, 100L))
            .willReturn(Optional.of(ticket));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(100L)).willReturn(Optional.of(event));
        given(admissionEligibilityPolicy.evaluate(
            Mockito.eq(100L),
            Mockito.same(event),
            Mockito.same(ticket),
            Mockito.same(exchangeCode),
            Mockito.any(OffsetDateTime.class)
        )).willReturn(expected);

        AdmissionEligibilityResult result = service.evaluateByTicketId(100L, 11L);

        assertThat(result).isSameAs(expected);
        verify(admissionTicketRepository).findByIdAndEventId(11L, 100L);
    }

    @Test
    void rejectsAdmissionTicketOutsideRequestedEventBeforePolicyEvaluation() {
        given(admissionTicketRepository.findByIdAndEventId(11L, 100L))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluateByTicketId(100L, 11L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);

        verify(admissionTicketRepository, never()).findById(11L);
        verifyNoInteractions(exchangeCodeRepository, eventRepository, admissionEligibilityPolicy);
    }

    @Test
    void resolvesQrTokenToTicketIdOnlyInsideRequestedEvent() {
        given(admissionTicketRepository.findByQrToken("secret-qr-token"))
            .willReturn(Optional.of(ticket()));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(100L)));

        Long ticketId = service.resolveTicketIdByQrToken(100L, " secret-qr-token ");

        assertThat(ticketId).isEqualTo(11L);
    }

    @Test
    void rejectsQrTokenFromAnotherEventBeforePromptContextIsBuilt() {
        given(admissionTicketRepository.findByQrToken("secret-qr-token"))
            .willReturn(Optional.of(ticket()));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(200L)));

        assertThatThrownBy(() -> service.resolveTicketIdByQrToken(100L, "secret-qr-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    private AdmissionTicket ticket() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return AdmissionTicket.builder()
            .id(11L)
            .exchangeCodeId(20L)
            .memberId(30L)
            .qrToken("secret-qr-token")
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(now.minusHours(1))
            .build();
    }

    private ExchangeCode exchangeCode(Long eventId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return ExchangeCode.builder()
            .id(20L)
            .eventId(eventId)
            .ticketOrderId(40L)
            .holderMemberId(30L)
            .code("EXCHANGE-CODE-SECRET")
            .status(ExchangeCodeStatus.REDEEMED)
            .expiresAt(now.plusDays(1))
            .redeemedAt(now.minusHours(1))
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusHours(1))
            .build();
    }

    private Event event(Long eventId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return Event.builder()
            .id(eventId)
            .organizerOrganizationId(1L)
            .name("event")
            .eventType("CONFERENCE")
            .description("event description")
            .venueName("venue")
            .address("Seoul")
            .startAt(now.minusHours(1))
            .endAt(now.plusHours(3))
            .ticketPrice(BigDecimal.valueOf(10000))
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(1)
            .ticketPurchaseLimit(2)
            .status(EventStatus.PUBLISHED)
            .boothRecruitmentEnabled(false)
            .venueMapEnabled(false)
            .boothReservationEnabled(false)
            .noShowGraceMinutes(10)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusHours(1))
            .build();
    }
}
