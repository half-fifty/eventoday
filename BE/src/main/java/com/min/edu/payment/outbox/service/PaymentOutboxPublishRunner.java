package com.min.edu.payment.outbox.service;

import org.springframework.stereotype.Component;

import com.min.edu.common.mail.EmailSender;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventType;
import com.min.edu.payment.outbox.dto.TicketReservationConfirmationEmailPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxPublishRunner {

    private final PaymentOutboxClaimService claimService;
    private final PaymentOutboxResultService resultService;
    private final EmailSender emailSender;
    private final TicketReservationConfirmationEmailFactory emailFactory;
    private final ObjectMapper objectMapper;

    public void publish(Long eventId, String leaseOwner) {
        claimService.claim(eventId, leaseOwner)
            .ifPresent(event -> sendClaimed(event, leaseOwner));
    }

    private void sendClaimed(PaymentOutboxEvent event, String leaseOwner) {
        try {
            send(event);
            resultService.markPublished(event.getId(), leaseOwner);
        } catch (Exception exception) {
            log.warn(
                "Payment outbox email send failed. id={}, eventType={}, retryCount={}",
                event.getId(),
                event.getEventType(),
                event.getRetryCount(),
                exception
            );
            resultService.markSendFailure(event.getId(), leaseOwner, exception);
        }
    }

    private void send(PaymentOutboxEvent event) throws Exception {
        if (event.getEventType() != PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL) {
            throw new IllegalStateException("Unsupported payment outbox event type: " + event.getEventType());
        }

        TicketReservationConfirmationEmailPayload payload = objectMapper.readValue(
            event.getPayload(),
            TicketReservationConfirmationEmailPayload.class
        );
        emailSender.send(emailFactory.create(payload));
    }
}
