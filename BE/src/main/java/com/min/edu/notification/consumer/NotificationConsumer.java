package com.min.edu.notification.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.min.edu.notification.config.KafkaTopicConfig;
import com.min.edu.notification.dto.NotificationCreateDto;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.service.NotificationService;
import com.min.edu.notification.sse.SseEmitterManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {
    private final NotificationService notificationService;
    private final SseEmitterManager sseEmitterManager;

    @KafkaListener(topics = KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC)
    public void consume(NotificationEventDto eventDto) {
        NotificationCreateDto createDto = new NotificationCreateDto(
                eventDto.memberId(),
                eventDto.notificationType(),
                eventDto.referenceType(),
                eventDto.referenceId(),
                eventDto.title(),
                eventDto.content());
        NotificationResponseDto savedNotification = notificationService.create(createDto);
        sseEmitterManager.send(eventDto.memberId(), savedNotification);
    }
}
