package com.min.edu.notification.outbox.scheduler;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;
import com.min.edu.notification.producer.NotificationProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * outbox_events 행 1건을 실제로 카프카에 발행한다.
 * - claim()으로 먼저 원자적으로 선점(PENDING/만료된 PROCESSING → PROCESSING)한 뒤에만 처리해서,
 *   여러 인스턴스가 동시에 같은 이벤트를 발행하는 것을 막는다.
 * - REQUIRES_NEW로 각 건을 독립된 트랜잭션에서 처리해, 한 건의 실패가 다른 건의 상태 갱신을 롤백시키지 않는다.
 * - @Async로 실행해 카프카 응답을 기다리는 동안 다음 폴링(relay()) 사이클을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxEventPublishRunner {

    private static final long LEASE_SECONDS = 30;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationProducer notificationProducer;
    private final ObjectMapper objectMapper;

    @Async("outboxRelayExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long outboxEventId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseExpiresAt = now.plusSeconds(LEASE_SECONDS);

        int claimed = outboxEventRepository.claim(outboxEventId, now, leaseExpiresAt);
        if (claimed == 0) {
            // 다른 스레드/인스턴스가 이미 선점해 처리 중이거나 처리를 끝냈다.
            return;
        }

        OutboxEvent event = outboxEventRepository.findById(outboxEventId).orElse(null);
        if (event == null) {
            return;
        }

        try {
            NotificationEventDto eventDto = objectMapper.readValue(event.getPayload(), NotificationEventDto.class);
            notificationProducer.send(eventDto);
            event.markPublished(OffsetDateTime.now());
        } catch (Exception exception) {
            log.warn("outbox 이벤트 발행 실패. id={}, retryCount={}", event.getId(), event.getRetryCount(), exception);
            event.markFailedAttempt(OffsetDateTime.now(), exception.getMessage());
        }
    }
}
