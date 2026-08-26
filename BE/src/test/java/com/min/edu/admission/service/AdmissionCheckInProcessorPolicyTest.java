package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionLog;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.policy.AdmissionEligibilityPolicy;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.repository.AdmissionLogRepository;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionCheckInProcessorPolicyTest {

    private final AdmissionTicketRepository admissionTicketRepository =
        Mockito.mock(AdmissionTicketRepository.class);
    private final ExchangeCodeRepository exchangeCodeRepository =
        Mockito.mock(ExchangeCodeRepository.class);
    private final AdmissionLogRepository admissionLogRepository =
        Mockito.mock(AdmissionLogRepository.class);
    private final EntityManager entityManager = Mockito.mock(EntityManager.class);
    private final PaymentFinalizationProperties paymentFinalizationProperties =
        Mockito.mock(PaymentFinalizationProperties.class);
    private final AdmissionEligibilityPolicy admissionEligibilityPolicy =
        Mockito.mock(AdmissionEligibilityPolicy.class);
    private final AdmissionCheckInProcessor processor = new AdmissionCheckInProcessor(
        admissionTicketRepository,
        exchangeCodeRepository,
        admissionLogRepository,
        entityManager,
        paymentFinalizationProperties,
        admissionEligibilityPolicy
    );

    @Test
    void checkInUsesSharedAdmissionEligibilityPolicyBeforeStateTransition() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        Event event = event(now);
        AdmissionTicket ticket = ticket(now);
        ExchangeCode exchangeCode = exchangeCode(now);
        givenLockTimeout();
        given(admissionTicketRepository.findByQrTokenForUpdate("qr-token"))
            .willReturn(Optional.of(ticket));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode));
        given(admissionEligibilityPolicy.evaluate(100L, event, ticket, exchangeCode, now))
            .willReturn(new AdmissionEligibilityResult(
                true,
                AdmissionEligibilityReasonCode.ELIGIBLE,
                AdmissionTicketStatus.ISSUED,
                EventStatus.PUBLISHED,
                event.getEndAt(),
                null
            ));
        given(admissionLogRepository.save(Mockito.any(AdmissionLog.class)))
            .willAnswer(invocation -> log(invocation.getArgument(0)));

        AdmissionCheckInProcessor.ProcessResult result =
            processor.checkIn(100L, event, "qr-token", "A Gate", 10L, now);

        verify(admissionEligibilityPolicy).evaluate(100L, event, ticket, exchangeCode, now);
        assertThat(result.outcome()).isEqualTo(AdmissionCheckInProcessor.Outcome.SUCCESS);
        assertThat(ticket.getStatus()).isEqualTo(AdmissionTicketStatus.USED);
    }

    private void givenLockTimeout() {
        Query query = Mockito.mock(Query.class);
        given(paymentFinalizationProperties.getFinalizationLockTimeoutMs()).willReturn(1000L);
        given(entityManager.createNativeQuery("select set_config('lock_timeout', :timeout, true)"))
            .willReturn(query);
        given(query.setParameter("timeout", "1000ms")).willReturn(query);
        given(query.getSingleResult()).willReturn("1000ms");
    }

    private AdmissionLog log(AdmissionLog source) {
        return AdmissionLog.builder()
            .id(30L)
            .admissionTicketId(source.getAdmissionTicketId())
            .staffMemberId(source.getStaffMemberId())
            .action(AdmissionAction.CHECK_IN)
            .result(AdmissionResult.SUCCESS)
            .gateName(source.getGateName())
            .processedAt(source.getProcessedAt())
            .build();
    }

    private Event event(OffsetDateTime now) {
        return Event.builder()
            .id(100L)
            .organizerOrganizationId(500L)
            .name("Eventoday Conference")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("Main Hall")
            .address("Seoul")
            .startAt(now.minusDays(1))
            .endAt(now.plusDays(1))
            .ticketPrice(BigDecimal.TEN)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(2)
            .status(EventStatus.PUBLISHED)
            .noShowGraceMinutes(10)
            .createdAt(now.minusDays(2))
            .updatedAt(now.minusDays(1))
            .build();
    }

    private AdmissionTicket ticket(OffsetDateTime now) {
        return AdmissionTicket.builder()
            .id(11L)
            .exchangeCodeId(20L)
            .memberId(40L)
            .qrToken("qr-token")
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(now.minusHours(1))
            .build();
    }

    private ExchangeCode exchangeCode(OffsetDateTime now) {
        return ExchangeCode.builder()
            .id(20L)
            .eventId(100L)
            .ticketOrderId(50L)
            .holderMemberId(40L)
            .code("EXCHANGE-CODE-SECRET")
            .status(ExchangeCodeStatus.REDEEMED)
            .expiresAt(now.plusDays(1))
            .redeemedAt(now.minusHours(1))
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusHours(1))
            .build();
    }
}
