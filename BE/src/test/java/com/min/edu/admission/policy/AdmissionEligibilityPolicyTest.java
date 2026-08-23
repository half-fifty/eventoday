package com.min.edu.admission.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AdmissionEligibilityPolicyTest {

    private final AdmissionEligibilityPolicy policy = new AdmissionEligibilityPolicy();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");

    @Test
    void rejectsEventNotPublished() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.APPROVED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.ISSUED), exchangeCode(100L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.EVENT_NOT_PUBLISHED);
    }

    @Test
    void rejectsEndedEvent() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now),
            ticket(AdmissionTicketStatus.ISSUED), exchangeCode(100L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.EVENT_ENDED);
    }

    @Test
    void rejectsUsedTicket() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.USED), exchangeCode(100L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.ALREADY_USED);
    }

    @Test
    void rejectsCancelledTicketAsNotIssued() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.CANCELLED), exchangeCode(100L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.TICKET_NOT_ISSUED);
    }

    @Test
    void rejectsExpiredTicketAsNotIssued() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.EXPIRED), exchangeCode(100L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.TICKET_NOT_ISSUED);
    }

    @Test
    void rejectsEventMismatch() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.ISSUED), exchangeCode(200L));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.EVENT_MISMATCH);
    }

    @Test
    void allowsIssuedTicketForPublishedNotEndedEvent() {
        AdmissionEligibilityResult result = evaluate(event(EventStatus.PUBLISHED, now.plusHours(1)),
            ticket(AdmissionTicketStatus.ISSUED), exchangeCode(100L));

        assertThat(result.eligible()).isTrue();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.ELIGIBLE);
    }

    private AdmissionEligibilityResult evaluate(
            Event event,
            AdmissionTicket ticket,
            ExchangeCode exchangeCode) {
        return policy.evaluate(100L, event, ticket, exchangeCode, now);
    }

    private Event event(EventStatus status, OffsetDateTime endAt) {
        return Event.builder()
            .id(100L)
            .organizerOrganizationId(500L)
            .name("Eventoday Conference")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("Main Hall")
            .address("Seoul")
            .startAt(now.minusDays(1))
            .endAt(endAt)
            .ticketPrice(BigDecimal.TEN)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(2)
            .status(status)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private AdmissionTicket ticket(AdmissionTicketStatus status) {
        return AdmissionTicket.builder()
            .id(10L)
            .exchangeCodeId(20L)
            .memberId(30L)
            .qrToken("qr-token")
            .status(status)
            .issuedAt(now.minusHours(1))
            .usedAt(status == AdmissionTicketStatus.USED ? now.minusMinutes(10) : null)
            .build();
    }

    private ExchangeCode exchangeCode(Long eventId) {
        return ExchangeCode.builder()
            .id(20L)
            .eventId(eventId)
            .ticketOrderId(40L)
            .holderMemberId(30L)
            .code("EXCHANGE-CODE-SECRET")
            .status(com.min.edu.admission.domain.ExchangeCodeStatus.ISSUED)
            .expiresAt(now.plusDays(1))
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    }
}
