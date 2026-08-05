package com.min.edu.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationCreateDto;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.service.NotificationService;
import com.min.edu.notification.sse.SseEmitterManager;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    private static final UUID EVENT_ID =
            UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad");

    @Mock
    private NotificationService notificationService;

    @Mock
    private SseEmitterManager sseEmitterManager;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void consume_savesNotificationBeforeSendingItThroughSse() {
        NotificationEventDto eventDto = new NotificationEventDto(
                EVENT_ID,
                1L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
        NotificationResponseDto savedResponse = new NotificationResponseDto(
                7L,
                eventDto.notificationType(),
                eventDto.referenceType(),
                eventDto.referenceId(),
                eventDto.title(),
                eventDto.content(),
                false,
                null,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"));
        given(notificationService.createIfAbsent(any(NotificationCreateDto.class)))
                .willReturn(Optional.of(savedResponse));

        notificationConsumer.consume(eventDto);

        ArgumentCaptor<NotificationCreateDto> captor =
                ArgumentCaptor.forClass(NotificationCreateDto.class);
        InOrder inOrder = inOrder(notificationService, sseEmitterManager);
        inOrder.verify(notificationService).createIfAbsent(captor.capture());
        inOrder.verify(sseEmitterManager).send(1L, savedResponse);

        NotificationCreateDto createDto = captor.getValue();
        assertThat(createDto.eventId()).isEqualTo(eventDto.eventId());
        assertThat(createDto.memberId()).isEqualTo(eventDto.memberId());
        assertThat(createDto.notificationType()).isEqualTo(eventDto.notificationType());
        assertThat(createDto.referenceType()).isEqualTo(eventDto.referenceType());
        assertThat(createDto.referenceId()).isEqualTo(eventDto.referenceId());
        assertThat(createDto.title()).isEqualTo(eventDto.title());
        assertThat(createDto.content()).isEqualTo(eventDto.content());
    }

    @Test
    void consume_doesNotSendSseWhenSameEventIsRedelivered() {
        NotificationEventDto eventDto = eventDto();
        NotificationResponseDto savedResponse = savedResponse(eventDto);
        given(notificationService.createIfAbsent(any(NotificationCreateDto.class)))
                .willReturn(Optional.of(savedResponse))
                .willReturn(Optional.empty());

        notificationConsumer.consume(eventDto);
        notificationConsumer.consume(eventDto);

        verify(notificationService, times(2))
                .createIfAbsent(any(NotificationCreateDto.class));
        verify(sseEmitterManager, times(1)).send(1L, savedResponse);
    }

    private NotificationEventDto eventDto() {
        return new NotificationEventDto(
                EVENT_ID,
                1L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.");
    }

    private NotificationResponseDto savedResponse(NotificationEventDto eventDto) {
        return new NotificationResponseDto(
                7L,
                eventDto.notificationType(),
                eventDto.referenceType(),
                eventDto.referenceId(),
                eventDto.title(),
                eventDto.content(),
                false,
                null,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"));
    }
}
