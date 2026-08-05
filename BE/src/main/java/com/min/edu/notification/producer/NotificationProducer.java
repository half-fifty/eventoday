package com.min.edu.notification.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.min.edu.notification.config.KafkaTopicConfig;
import com.min.edu.notification.dto.NotificationEventDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationProducer {
    private final KafkaTemplate<String, NotificationEventDto> kafkaTemplate;

    public void send(NotificationEventDto eventDto) {
        String key = eventDto.memberId().toString();

        kafkaTemplate.send(
                KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC,
                key,
                eventDto);
    }
}
