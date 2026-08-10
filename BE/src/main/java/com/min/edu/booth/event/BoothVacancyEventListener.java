package com.min.edu.booth.event;

import com.min.edu.booth.service.BoothVacancyNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 부스 빈자리 알림 이벤트 리스너
 * - 예약 취소 트랜잭션 커밋 후 비동기로 실행
 * - 결정적 idempotencyKey로 멱등성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoothVacancyEventListener {

    private final BoothVacancyNotificationService vacancyNotificationService;

    /**
     * 트랜잭션 커밋 후 비동기로 알림 생성
     * - AFTER_COMMIT: 커밋 성공 후 실행
     * - @Async: 별도 스레드에서 비동기 처리
     * - 알림 실패해도 예약 취소는 이미 DB에 반영됨
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("vacancyNotificationExecutor")
    public void handleBoothVacancy(BoothVacancyEvent event) {
        try {
            // 결정적 Idempotency Key 생성 (Long 타입)
            Long deterministicIdempotencyKey = generateDeterministicIdempotencyKey(event);

            log.info("Processing booth vacancy notification - boothId: {}, slotId: {}, idempotencyKey: {}",
                    event.getBoothId(), event.getSlotId(), deterministicIdempotencyKey);

            vacancyNotificationService.notifyVacancyWithIdempotency(
                    event.getBoothId(),
                    event.getDisplayName(),
                    event.getSlotId(),
                    deterministicIdempotencyKey  // ← Long 타입
            );

            log.info("Vacancy notification sent successfully - boothId: {}, slotId: {}",
                    event.getBoothId(), event.getSlotId());

        } catch (Exception e) {
            log.error("Failed to send vacancy notification - boothId: {}, slotId: {}, error: {}",
                    event.getBoothId(), event.getSlotId(), e.getMessage(), e);
            // TODO: 모니터링 시스템(Sentry, DataDog 등)에 알림 전송
        }
    }

    /**
     * 결정적 Idempotency Key 생성 (Long 타입)
     * - event.boothId + event.slotId + timestamp 기반으로 생성
     * - 같은 부스/슬롯 조합이면 항상 같은 키 생성 (멱등성 보장)
     * - String.hashCode()를 Long으로 변환해 일관된 식별자 제공
     */
    private Long generateDeterministicIdempotencyKey(BoothVacancyEvent event) {
        try {
            String input = String.format("booth:%d|slot:%d|timestamp:%d",
                    event.getBoothId(),
                    event.getSlotId(),
                    event.getEventTimestamp()
            );

            // String을 hashCode()로 Long 변환
            // Math.abs()로 음수 방지
            return Math.abs((long) input.hashCode());

        } catch (Exception e) {
            log.warn("Failed to generate deterministic idempotency key, using fallback", e);
            // Fallback: slot_id의 hashCode 사용
            return Math.abs((long) event.getSlotId().hashCode());
        }
    }
}