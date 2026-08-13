package com.min.edu.notification.outbox.scheduler;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;
import com.min.edu.notification.producer.NotificationProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * outbox_events 행 1건을 실제로 카프카에 발행한다.
 * REQUIRES_NEW로 각 건을 독립된 트랜잭션에서 처리해, 한 건의 실패가 다른 건의
 * 처리(및 그 행의 상태 갱신)를 롤백시키지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxEventPublishRunner {

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationProducer notificationProducer;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long outboxEventId) {
        OutboxEvent event = outboxEventRepository.findById(outboxEventId).orElse(null);

        if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        try {
            NotificationEventDto eventDto = objectMapper.readValue(event.getPayload(), NotificationEventDto.class);
            notificationProducer.send(eventDto);
            event.markPublished(now);
        } catch (Exception exception) {
            log.warn("outbox 이벤트 발행 실패. id={}, retryCount={}", event.getId(), event.getRetryCount(), exception);
            event.markFailedAttempt(now, exception.getMessage());
        }
    }
}
