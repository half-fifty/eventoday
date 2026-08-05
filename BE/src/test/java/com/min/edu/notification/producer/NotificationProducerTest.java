package com.min.edu.notification.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.min.edu.notification.config.KafkaTopicConfig;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, NotificationEventDto> kafkaTemplate;

    @InjectMocks
    private NotificationProducer notificationProducer;

    @Test
    void send_publishesEventWithMemberIdAsKafkaKey() {
        NotificationEventDto eventDto = new NotificationEventDto(
                UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad"),
                42L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");

        notificationProducer.send(eventDto);

        verify(kafkaTemplate).send(
                KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC,
                "42",
                eventDto);
    }

    @Test
    void create_assignsUniqueEventIdBeforePublishing() {
        NotificationEventDto eventDto = NotificationEventDto.create(
                42L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");

        assertThat(eventDto.eventId()).isNotNull();
    }
}
