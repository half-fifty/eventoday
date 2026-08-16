package com.min.edu.notification.outbox.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;
import com.min.edu.notification.producer.NotificationProducer;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublishRunnerTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private NotificationProducer notificationProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxEventPublishRunner runner() {
        return new OutboxEventPublishRunner(outboxEventRepository, notificationProducer, objectMapper);
    }

    private String payload() {
        NotificationEventDto eventDto = NotificationEventDto.create(
            42L, NotificationType.EVENT_SCHEDULE_CHANGED, "EVENT", 10L, "일정 변경", "행사 일정이 변경되었습니다."
        );
        return objectMapper.writeValueAsString(eventDto);
    }

    private void givenClaimSucceeds() {
        given(outboxEventRepository.claim(anyLong(), any(), any())).willReturn(1);
    }

    @Test
    void publish_claimSucceeds_marksEventPublished() {
        givenClaimSucceeds();
        OutboxEvent event = OutboxEvent.create("42", payload(), OffsetDateTime.now());
        given(outboxEventRepository.findById(1L)).willReturn(Optional.of(event));

        runner().publish(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(notificationProducer).send(any());
    }

    @Test
    void publish_kafkaSendThrows_marksFailedAttemptInsteadOfPropagating() {
        givenClaimSucceeds();
        OutboxEvent event = OutboxEvent.create("42", payload(), OffsetDateTime.now());
        given(outboxEventRepository.findById(1L)).willReturn(Optional.of(event));
        willThrow(new IllegalStateException("카프카 발행에 실패했습니다.")).given(notificationProducer).send(any());

        runner().publish(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    void publish_claimFails_doesNothing() {
        // 다른 인스턴스/스레드가 이미 선점했거나(0건 갱신), 이미 처리된 이벤트인 경우
        given(outboxEventRepository.claim(anyLong(), any(), any())).willReturn(0);

        runner().publish(1L);

        verify(notificationProducer, never()).send(any());
        verify(outboxEventRepository, never()).findById(1L);
    }

    @Test
    void publish_claimSucceedsButEventMissing_doesNothing() {
        givenClaimSucceeds();
        given(outboxEventRepository.findById(1L)).willReturn(Optional.empty());

        runner().publish(1L);

        verify(notificationProducer, never()).send(any());
    }
}
