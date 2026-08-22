package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.payment.outbox.domain.PaymentOutboxEventType;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxWriterTest {

    @Mock
    private PaymentOutboxEventRepository repository;

    @Test
    void appendTicketReservationConfirmation_savesMinimalSafePayload() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());
        UUID eventId = UUID.randomUUID();
        given(repository.insertPending(
            any(UUID.class),
            anyString(),
            anyString(),
            anyString(),
            any(OffsetDateTime.class)
        )).willReturn(1);

        writer.appendTicketReservationConfirmation(
            eventId,
            "ORDER-1",
            "guest@example.com",
            "test-event"
        );

        verify(repository).insertPending(
            any(UUID.class),
            org.mockito.ArgumentMatchers.eq(PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL.name()),
            org.mockito.ArgumentMatchers.eq("ORDER-1"),
            org.mockito.ArgumentMatchers.argThat(payload ->
                payload.contains("ORDER-1")
                    && payload.contains("guest@example.com")
                    && payload.contains("test-event")
                    && !payload.contains("paymentKey")
                    && !payload.contains("orderAccessToken")
                    && !payload.contains("providerPayload")
                    && !payload.contains("010-1234-5678")
            ),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void appendTicketReservationConfirmation_skipsBlankRecipient() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());

        writer.appendTicketReservationConfirmation("ORDER-1", " ", "event");

        verify(repository, never()).insertPending(any(), any(), any(), any(), any());
    }

    @Test
    void appendFunnelCompletePayment_savesCompletePaymentPayload() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());
        given(repository.insertPending(
            any(UUID.class),
            anyString(),
            anyString(),
            anyString(),
            any(OffsetDateTime.class)
        )).willReturn(1);

        writer.appendFunnelCompletePayment("session-1", 42L, "anon-1", null, OffsetDateTime.now());

        verify(repository).insertPending(
            any(UUID.class),
            org.mockito.ArgumentMatchers.eq(PaymentOutboxEventType.PUBLISH_FUNNEL_COMPLETE_PAYMENT.name()),
            org.mockito.ArgumentMatchers.eq("session-1"),
            org.mockito.ArgumentMatchers.argThat(payload ->
                payload.contains("session-1")
                    && payload.contains("\"eventId\":42")
                    && payload.contains("anon-1")
                    && payload.contains("COMPLETE_PAYMENT")
            ),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void appendFunnelCompletePayment_skipsWhenSessionIdMissing() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());

        writer.appendFunnelCompletePayment(null, 42L, "anon-1", null, OffsetDateTime.now());
        writer.appendFunnelCompletePayment(" ", 42L, "anon-1", null, OffsetDateTime.now());

        verify(repository, never()).insertPending(any(), any(), any(), any(), any());
    }
}
