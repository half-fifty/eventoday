package com.min.edu.notification.outbox.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
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
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
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
