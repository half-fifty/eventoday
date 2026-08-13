package com.min.edu.notification.outbox.scheduler;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * outbox_events 테이블을 주기적으로 훑어 PENDING 상태(재시도 대기 시각이 지난)인
 * 이벤트를 카프카로 발행한다. 실제 발행 트랜잭션 로직은 OutboxEventPublishRunner에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRelayScheduler {

    private static final long FIXED_DELAY_MILLIS = 2_000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublishRunner publishRunner;

    @Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository
            .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                OutboxEventStatus.PENDING, OffsetDateTime.now()
            );

        for (OutboxEvent event : pending) {
            publishRunner.publish(event.getId());
        }
    }
}
