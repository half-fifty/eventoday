package com.min.edu.notification.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    private OutboxEventWriter writer() {
        return new OutboxEventWriter(outboxEventRepository, new ObjectMapper());
    }

    @Test
    void write_savesOutboxEventWithMemberIdAsMessageKeyAndJsonPayload() {
        NotificationEventDto eventDto = new NotificationEventDto(
            UUID.fromString("b9d8a554-940f-4d72-b6de-711616158aad"),
            42L,
            NotificationType.EVENT_SCHEDULE_CHANGED,
            "EVENT",
            10L,
            "일정 변경",
            "행사 일정이 변경되었습니다."
        );
        given(outboxEventRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        writer().write(eventDto);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getMessageKey()).isEqualTo("42");
        assertThat(saved.getPayload()).contains("일정 변경").contains("EVENT_SCHEDULE_CHANGED");
    }
}
