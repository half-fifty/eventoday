package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
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
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        writer.appendTicketReservationConfirmation(
            "ORDER-1",
            "guest@example.com",
            "test-event"
        );

        ArgumentCaptor<PaymentOutboxEvent> captor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(repository).save(captor.capture());
        PaymentOutboxEvent saved = captor.getValue();

        assertThat(saved.getEventType())
            .isEqualTo(PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL);
        assertThat(saved.getAggregateId()).isEqualTo("ORDER-1");
        assertThat(saved.getStatus()).isEqualTo(PaymentOutboxEventStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getPayload()).contains("ORDER-1", "guest@example.com", "test-event");
        assertThat(saved.getPayload())
            .doesNotContain("paymentKey", "orderAccessToken", "qr", "Toss", "providerPayload", "010-1234-5678");
    }

    @Test
    void appendTicketReservationConfirmation_skipsExistingBusinessEvent() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());
        given(repository.existsByEventTypeAndAggregateId(
            PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL,
            "ORDER-1"
        )).willReturn(true);

        writer.appendTicketReservationConfirmation("ORDER-1", "guest@example.com", "event");

        verify(repository, never()).save(any());
    }

    @Test
    void appendTicketReservationConfirmation_skipsBlankRecipient() {
        PaymentOutboxWriter writer = new PaymentOutboxWriter(repository, new ObjectMapper());

        writer.appendTicketReservationConfirmation("ORDER-1", " ", "event");

        verify(repository, never()).save(any());
    }
}
