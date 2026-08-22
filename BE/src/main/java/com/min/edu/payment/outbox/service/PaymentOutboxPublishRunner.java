package com.min.edu.payment.outbox.service;

import org.springframework.stereotype.Component;

import com.min.edu.common.mail.EmailSender;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.producer.FunnelActionProducer;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.dto.TicketReservationConfirmationEmailPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxPublishRunner {

    private final PaymentOutboxClaimService claimService;
    private final PaymentOutboxLeaseService leaseService;
    private final PaymentOutboxLeaseHeartbeat leaseHeartbeat;
    private final PaymentOutboxResultService resultService;
    private final EmailSender emailSender;
    private final TicketReservationConfirmationEmailFactory emailFactory;
    private final FunnelActionProducer funnelActionProducer;
    private final ObjectMapper objectMapper;

    public void publish(Long eventId, String leaseOwner) {
        claimService.claim(eventId, leaseOwner)
            .ifPresent(event -> sendClaimed(event, leaseOwner));
    }

    private void sendClaimed(PaymentOutboxEvent event, String leaseOwner) {
        if (!leaseService.renew(event.getId(), leaseOwner)) {
            log.warn(
                "Payment outbox send skipped after ownership loss. id={}, eventType={}, retryCount={}",
                event.getId(),
                event.getEventType(),
                event.getRetryCount()
            );
            return;
        }

        try (PaymentOutboxLeaseHeartbeat.LeaseHeartbeat ignored =
                 leaseHeartbeat.start(event.getId(), leaseOwner)) {
            send(event);
            resultService.markPublished(event.getId(), leaseOwner);
        } catch (Exception exception) {
            log.warn(
                "Payment outbox email send failed. id={}, eventType={}, retryCount={}, errorType={}",
                event.getId(),
                event.getEventType(),
                event.getRetryCount(),
                exception.getClass().getName()
            );
            resultService.markSendFailure(event.getId(), leaseOwner, exception);
        }
    }

    private void send(PaymentOutboxEvent event) throws Exception {
        switch (event.getEventType()) {
            case SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL -> sendEmail(event);
            case PUBLISH_FUNNEL_COMPLETE_PAYMENT -> publishFunnelEvent(event);
        }
    }

    private void sendEmail(PaymentOutboxEvent event) throws Exception {
        TicketReservationConfirmationEmailPayload payload = objectMapper.readValue(
            event.getPayload(),
            TicketReservationConfirmationEmailPayload.class
        );
        emailSender.send(emailFactory.create(payload));
    }

    // 발행 자체(Kafka 전송)는 FunnelActionProducer가 비동기 콜백으로 성공/실패를 처리하므로
    // 여기서는 예외를 던지지 않는다 — outbox는 "produce 호출까지 성공"을 기준으로 완료 처리한다.
    private void publishFunnelEvent(PaymentOutboxEvent event) throws Exception {
        FunnelActionEventDto eventDto = objectMapper.readValue(event.getPayload(), FunnelActionEventDto.class);
        funnelActionProducer.send(eventDto);
    }
}
