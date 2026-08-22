package com.min.edu.payment.outbox.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.producer.FunnelActionProducer;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;

import tools.jackson.databind.ObjectMapper;

class PaymentOutboxPublishRunnerTest {

    private final PaymentOutboxClaimService claimService = Mockito.mock(PaymentOutboxClaimService.class);
    private final PaymentOutboxLeaseService leaseService = Mockito.mock(PaymentOutboxLeaseService.class);
    private final PaymentOutboxLeaseHeartbeat leaseHeartbeat = Mockito.mock(PaymentOutboxLeaseHeartbeat.class);
    private final PaymentOutboxResultService resultService = Mockito.mock(PaymentOutboxResultService.class);
    private final EmailSender emailSender = Mockito.mock(EmailSender.class);
    private final FunnelActionProducer funnelActionProducer = Mockito.mock(FunnelActionProducer.class);
    private final PaymentOutboxPublishRunner runner = new PaymentOutboxPublishRunner(
        claimService,
        leaseService,
        leaseHeartbeat,
        resultService,
        emailSender,
        new TicketReservationConfirmationEmailFactory(),
        funnelActionProducer,
        new ObjectMapper()
    );

    @Test
    void publish_sendsEmailOutsideClaimAndMarksPublished() {
        given(claimService.claim(1L, "owner-1")).willReturn(Optional.of(event()));
        given(leaseService.renew(1L, "owner-1")).willReturn(true);

        runner.publish(1L, "owner-1");

        verify(emailSender).send(any(EmailMessage.class));
        verify(resultService).markPublished(1L, "owner-1");
    }

    @Test
    void publish_sendFailureMarksRetry() {
        RuntimeException failure = new RuntimeException("smtp down");
        given(claimService.claim(1L, "owner-1")).willReturn(Optional.of(event()));
        given(leaseService.renew(1L, "owner-1")).willReturn(true);
        Mockito.doThrow(failure).when(emailSender).send(any(EmailMessage.class));

        runner.publish(1L, "owner-1");

        verify(resultService).markSendFailure(1L, "owner-1", failure);
        verify(resultService, never()).markPublished(any(), any());
    }

    @Test
    void publish_ownershipLostBeforeSendSkipsEmailAndResultUpdate() {
        given(claimService.claim(1L, "owner-1")).willReturn(Optional.of(event()));
        given(leaseService.renew(1L, "owner-1")).willReturn(false);

        runner.publish(1L, "owner-1");

        verify(emailSender, never()).send(any());
        verify(resultService, never()).markPublished(any(), any());
        verify(resultService, never()).markSendFailure(any(), any(), any());
    }

    @Test
    void publish_claimFailureDoesNotSend() {
        given(claimService.claim(1L, "owner-1")).willReturn(Optional.empty());

        runner.publish(1L, "owner-1");

        verify(emailSender, never()).send(any());
    }

    @Test
    void publish_funnelEventType_sendsToFunnelActionProducer() {
        given(claimService.claim(2L, "owner-1")).willReturn(Optional.of(funnelEvent()));
        given(leaseService.renew(2L, "owner-1")).willReturn(true);

        runner.publish(2L, "owner-1");

        verify(funnelActionProducer).send(any(FunnelActionEventDto.class));
        verify(resultService).markPublished(2L, "owner-1");
    }

    private PaymentOutboxEvent event() {
        return PaymentOutboxEvent.builder()
            .id(1L)
            .eventType(com.min.edu.payment.outbox.domain.PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL)
            .aggregateId("ORDER-1")
            .payload("{\"orderNo\":\"ORDER-1\",\"buyerEmail\":\"guest@example.com\",\"eventName\":\"event\"}")
            .status(com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PROCESSING)
            .retryCount(0)
            .availableAt(OffsetDateTime.now())
            .leaseOwner("owner-1")
            .leaseUntil(OffsetDateTime.now().plusSeconds(30))
            .createdAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOutboxEvent funnelEvent() {
        return PaymentOutboxEvent.builder()
            .id(2L)
            .eventType(com.min.edu.payment.outbox.domain.PaymentOutboxEventType.PUBLISH_FUNNEL_COMPLETE_PAYMENT)
            .aggregateId("session-1")
            .payload("{\"actionId\":\"a1\",\"sessionId\":\"session-1\",\"eventId\":42,\"anonymousId\":\"anon-1\","
                + "\"userId\":null,\"actionType\":\"COMPLETE_PAYMENT\",\"occurredAt\":\"2026-08-22T10:00:00+09:00\","
                + "\"receivedAt\":\"2026-08-22T10:00:00+09:00\",\"properties\":{}}")
            .status(com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PROCESSING)
            .retryCount(0)
            .availableAt(OffsetDateTime.now())
            .leaseOwner("owner-1")
            .leaseUntil(OffsetDateTime.now().plusSeconds(30))
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
