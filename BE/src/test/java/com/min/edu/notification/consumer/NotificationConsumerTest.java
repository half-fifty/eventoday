package com.min.edu.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import java.time.OffsetDateTime;

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

    @Mock
    private NotificationService notificationService;

    @Mock
    private SseEmitterManager sseEmitterManager;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void consume_savesNotificationBeforeSendingItThroughSse() {
        NotificationEventDto eventDto = new NotificationEventDto(
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
        given(notificationService.create(any(NotificationCreateDto.class)))
                .willReturn(savedResponse);

        notificationConsumer.consume(eventDto);

        ArgumentCaptor<NotificationCreateDto> captor =
                ArgumentCaptor.forClass(NotificationCreateDto.class);
        InOrder inOrder = inOrder(notificationService, sseEmitterManager);
        inOrder.verify(notificationService).create(captor.capture());
        inOrder.verify(sseEmitterManager).send(1L, savedResponse);

        NotificationCreateDto createDto = captor.getValue();
        assertThat(createDto.memberId()).isEqualTo(eventDto.memberId());
        assertThat(createDto.notificationType()).isEqualTo(eventDto.notificationType());
        assertThat(createDto.referenceType()).isEqualTo(eventDto.referenceType());
        assertThat(createDto.referenceId()).isEqualTo(eventDto.referenceId());
        assertThat(createDto.title()).isEqualTo(eventDto.title());
        assertThat(createDto.content()).isEqualTo(eventDto.content());
    }
}
