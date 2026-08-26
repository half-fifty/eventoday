package com.min.edu.notification.producer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.min.edu.notification.config.KafkaTopicConfig;
import com.min.edu.notification.dto.NotificationEventDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, NotificationEventDto> kafkaTemplate;

    /**
     * 카프카 발행 결과를 동기적으로 기다린다. outbox 릴레이가 발행 성공 여부를
     * 정확히 알아야 성공한 것만 PUBLISHED로 표시하고 실패한 것만 재시도할 수 있기 때문이다.
     */
    public void send(NotificationEventDto eventDto) {
        String key = eventDto.memberId().toString();

        try {
            kafkaTemplate.send(
                    KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC,
                    key,
                    eventDto)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("카프카 발행이 중단되었습니다.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("카프카 발행에 실패했습니다.", exception);
        }
    }
}
