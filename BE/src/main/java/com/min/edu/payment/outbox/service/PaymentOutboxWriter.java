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
    // aggregate_id는 orderId(주문 단위)를 쓴다 — funnelSessionId를 쓰면, 같은 브라우징 세션에서
    // 서로 다른 행사를 둘 다 구매했을 때 (event_type, aggregate_id) unique 제약에 걸려
    // 두 번째 주문의 COMPLETE_PAYMENT가 조용히 무시된다.
    @Transactional
    public void appendFunnelCompletePayment(
            Long orderId,
            String funnelSessionId,
            Long eventId,
            String anonymousId,
            Long userId,
            OffsetDateTime occurredAt) {
        appendFunnelCompletePayment(
                UUID.randomUUID(), orderId, funnelSessionId, eventId, anonymousId, userId, occurredAt);
    }

    @Transactional
    void appendFunnelCompletePayment(
            UUID outboxEventId,
            Long orderId,
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
            String.valueOf(orderId),
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
