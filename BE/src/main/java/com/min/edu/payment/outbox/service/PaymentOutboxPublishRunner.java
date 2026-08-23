package com.min.edu.payment.outbox.service;

import java.time.Duration;

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

    private static final Duration FUNNEL_EVENT_SEND_TIMEOUT = Duration.ofSeconds(5);

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
        } catch (InterruptedException interruptedException) {
            // Future.get()의 대기 중 인터럽트는 인터럽트 상태를 초기화하고 예외만 던진다.
            // 여기서 복원하지 않으면 스레드 풀 종료/취소 신호가 유실된다.
            Thread.currentThread().interrupt();
            log.warn(
                "Payment outbox send interrupted. id={}, eventType={}, retryCount={}",
                event.getId(),
                event.getEventType(),
                event.getRetryCount()
            );
            resultService.markSendFailure(event.getId(), leaseOwner, interruptedException);
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

    // 브로커 ack을 기다렸다가 실패/타임아웃이면 예외를 던져 markSendFailure로 재시도되게 한다
    // (fire-and-forget인 FunnelActionProducer.send와 달리, outbox는 실제 발행 성공을 기준으로
    // 완료 처리해야 이벤트 유실을 막을 수 있다).
    private void publishFunnelEvent(PaymentOutboxEvent event) throws Exception {
        FunnelActionEventDto eventDto = objectMapper.readValue(event.getPayload(), FunnelActionEventDto.class);
        funnelActionProducer.sendAndWait(eventDto, FUNNEL_EVENT_SEND_TIMEOUT);
    }
}
