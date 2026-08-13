package com.min.edu.notification.outbox.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelaySchedulerTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    // OutboxEventPublishRunner는 package-private이지만 이 테스트가 같은 패키지라 mock 가능하다.
    private final OutboxEventPublishRunner publishRunner = mock(OutboxEventPublishRunner.class);

    private OutboxEventRelayScheduler scheduler() {
        return new OutboxEventRelayScheduler(outboxEventRepository, publishRunner);
    }

    @Test
    void relay_dispatchesPublishForEachCandidate() {
        OutboxEvent first = OutboxEvent.create("1", "{}", OffsetDateTime.now());
        setId(first, 10L);
        OutboxEvent second = OutboxEvent.create("2", "{}", OffsetDateTime.now());
        setId(second, 11L);
        given(outboxEventRepository.findClaimableCandidates(any(), any(Pageable.class)))
            .willReturn(List.of(first, second));

        scheduler().relay();

        verify(publishRunner).publish(10L);
        verify(publishRunner).publish(11L);
    }

    @Test
    void relay_noCandidates_doesNotCallPublish() {
        given(outboxEventRepository.findClaimableCandidates(any(), any(Pageable.class)))
            .willReturn(List.of());

        scheduler().relay();

        verify(publishRunner, times(0)).publish(any());
    }

    private void setId(OutboxEvent event, Long id) {
        try {
            var field = OutboxEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
