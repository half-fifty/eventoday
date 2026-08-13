package com.min.edu.notification.outbox.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 이벤트를 카프카로 직접 보내는 대신 outbox_events 테이블에 기록한다.
 * 실제 발행은 {@code OutboxEventPublishRunner}가 별도 스케줄러에서 담당한다.
 *
 * <p>호출부(BoothApplicationNotificationListener, EventReviewNotificationListener)는
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}에서 이 메서드를 부른다.
 * 그 시점엔 원본 트랜잭션이 DB 레벨로는 이미 커밋됐지만 스레드에 트랜잭션 리소스가
 * 아직 남아있을 수 있어, 기본 전파 방식(REQUIRED)을 쓰면 그 "이미 끝난" 트랜잭션에
 * 참여해버려 save()가 커밋되지 않고 조용히 사라질 수 있다. REQUIRES_NEW로 완전히
 * 새 트랜잭션을 강제해야 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(NotificationEventDto eventDto) {
        String payload = serialize(eventDto);
        OffsetDateTime now = OffsetDateTime.now();

        outboxEventRepository.save(
            OutboxEvent.create(eventDto.memberId().toString(), payload, now)
        );
    }

    private String serialize(NotificationEventDto eventDto) {
        try {
            return objectMapper.writeValueAsString(eventDto);
        } catch (JacksonException exception) {
            throw new IllegalStateException("알림 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}
