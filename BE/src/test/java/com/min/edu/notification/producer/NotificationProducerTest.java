package com.min.edu.notification.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.min.edu.notification.config.KafkaTopicConfig;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, NotificationEventDto> kafkaTemplate;

    @InjectMocks
    private NotificationProducer notificationProducer;

    private NotificationEventDto eventDto() {
        return new NotificationEventDto(
                UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad"),
                42L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_publishesEventWithMemberIdAsKafkaKey() {
        NotificationEventDto eventDto = eventDto();
        SendResult<String, NotificationEventDto> sendResult = mock(SendResult.class);
        given(kafkaTemplate.send(KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC, "42", eventDto))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        notificationProducer.send(eventDto);

        verify(kafkaTemplate).send(
                KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC,
                "42",
                eventDto);
    }

    @Test
    void send_kafkaSendFails_throwsIllegalStateException() {
        NotificationEventDto eventDto = eventDto();
        CompletableFuture<SendResult<String, NotificationEventDto>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("브로커 응답 없음"));
        given(kafkaTemplate.send(KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC, "42", eventDto))
                .willReturn(failedFuture);

        assertThatThrownBy(() -> notificationProducer.send(eventDto))
                .isInstanceOf(IllegalStateException.class);
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
