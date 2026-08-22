package com.min.edu.payment.outbox.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.funnel.dto.FunnelActionEventDto;
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

    @Transactional
    public void appendTicketReservationConfirmation(
            String orderNo,
            String buyerEmail,
            String eventName) {
        appendTicketReservationConfirmation(UUID.randomUUID(), orderNo, buyerEmail, eventName);
    }

    @Transactional
    void appendTicketReservationConfirmation(
            UUID eventId,
            String orderNo,
            String buyerEmail,
            String eventName) {
        if (orderNo == null || orderNo.isBlank()
                || buyerEmail == null || buyerEmail.isBlank()) {
            return;
        }

        paymentOutboxEventRepository.insertPending(
            eventId,
            PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL.name(),
            orderNo,
            serialize(new TicketReservationConfirmationEmailPayload(orderNo, buyerEmail, eventName)),
            OffsetDateTime.now()
        );
    }

    // sessionId가 없으면 (구버전 클라이언트 등) 어느 퍼널 세션에 붙일지 알 수 없어 건너뛴다.
    @Transactional
    public void appendFunnelCompletePayment(
            String funnelSessionId,
            Long eventId,
            String anonymousId,
            Long userId,
            OffsetDateTime occurredAt) {
        appendFunnelCompletePayment(UUID.randomUUID(), funnelSessionId, eventId, anonymousId, userId, occurredAt);
    }

    @Transactional
    void appendFunnelCompletePayment(
            UUID outboxEventId,
            String funnelSessionId,
            Long eventId,
            String anonymousId,
            Long userId,
            OffsetDateTime occurredAt) {
        if (funnelSessionId == null || funnelSessionId.isBlank()) {
            return;
        }

        FunnelActionEventDto eventDto = FunnelActionEventDto.create(
            funnelSessionId,
            eventId,
            anonymousId,
            userId,
            "COMPLETE_PAYMENT",
            occurredAt,
            Map.of()
        );

        paymentOutboxEventRepository.insertPending(
            outboxEventId,
            PaymentOutboxEventType.PUBLISH_FUNNEL_COMPLETE_PAYMENT.name(),
            funnelSessionId,
            serialize(eventDto),
            occurredAt
        );
    }

    private String serialize(TicketReservationConfirmationEmailPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize payment outbox payload.", exception);
        }
    }

    private String serialize(FunnelActionEventDto payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize payment outbox payload.", exception);
        }
    }
}
