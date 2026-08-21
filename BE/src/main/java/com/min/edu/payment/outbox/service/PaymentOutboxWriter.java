package com.min.edu.payment.outbox.service;

import java.time.OffsetDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventType;
import com.min.edu.payment.outbox.dto.TicketReservationConfirmationEmailPayload;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PaymentOutboxWriter {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final ObjectMapper objectMapper;

    public void appendTicketReservationConfirmation(
            String orderNo,
            String buyerEmail,
            String eventName) {
        if (orderNo == null || orderNo.isBlank()
                || buyerEmail == null || buyerEmail.isBlank()) {
            return;
        }

        if (paymentOutboxEventRepository.existsByEventTypeAndAggregateId(
                PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL,
                orderNo)) {
            return;
        }

        try {
            paymentOutboxEventRepository.save(PaymentOutboxEvent.ticketReservationConfirmation(
                orderNo,
                serialize(new TicketReservationConfirmationEmailPayload(orderNo, buyerEmail, eventName)),
                OffsetDateTime.now()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent duplicate business invocation lost the unique-key race.
        }
    }

    private String serialize(TicketReservationConfirmationEmailPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize payment outbox payload.", exception);
        }
    }
}
