package com.min.edu.notification.outbox.scheduler;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * outbox_events 테이블을 주기적으로 훑어 발행 대상 후보(PENDING 재시도 대기 종료 /
 * lease 만료된 PROCESSING)를 찾아 OutboxEventPublishRunner에 위임한다.
 *
 * <p>publishRunner.publish()는 {@code @Async}라 즉시 반환되므로, 이 메서드는
 * 카프카 응답을 기다리지 않고 바로 끝난다 — fixedDelay 다음 실행이 느린 발행 때문에
 * 지연되지 않는다. 실제 중복 처리 방지는 후보 조회가 아니라 claim()의 원자적 조건부
 * UPDATE가 담당하므로, 같은 후보가 여러 번 조회돼도 안전하다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRelayScheduler {

    private static final long FIXED_DELAY_MILLIS = 2_000;
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublishRunner publishRunner;

    @Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
    public void relay() {
        Pageable limit = PageRequest.of(0, BATCH_SIZE);
        List<OutboxEvent> candidates = outboxEventRepository
            .findClaimableCandidates(OffsetDateTime.now(), limit);

        for (OutboxEvent event : candidates) {
            publishRunner.publish(event.getId());
        }
    }
}
