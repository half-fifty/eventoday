package com.min.edu.notification.outbox.scheduler;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.notification.outbox.domain.OutboxEventStatus;
import com.min.edu.notification.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 발행이 끝난 outbox 행을 무한정 쌓아두지 않도록 일정 기간이 지나면 정리한다.
 * PENDING/FAILED 행은 건드리지 않는다 — 아직 처리 안 됐거나 실패 원인 확인이 필요하기 때문.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventCleanupScheduler {

    private static final long FIXED_RATE_MILLIS = 60L * 60 * 1000;
    private static final int RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(fixedRate = FIXED_RATE_MILLIS)
    @Transactional
    public void cleanupPublishedEvents() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = outboxEventRepository.deleteByStatusAndPublishedAtBefore(
            OutboxEventStatus.PUBLISHED, threshold
        );

        if (deleted > 0) {
            log.info("outbox 발행 완료 이벤트 {}건 정리", deleted);
        }
    }
}
