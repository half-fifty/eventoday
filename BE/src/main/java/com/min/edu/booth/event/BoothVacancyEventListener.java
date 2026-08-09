package com.min.edu.booth.event;

import com.min.edu.booth.service.BoothVacancyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 부스 빈자리 알림 이벤트 리스너
 * - 예약 취소 트랜잭션 커밋 후 비동기로 실행
 * - idempotencyKey로 재처리 시 중복 방지
 */
@Component
@RequiredArgsConstructor
public class BoothVacancyEventListener {

    private final BoothVacancyNotificationService vacancyNotificationService;

    /**
     * 트랜잭션 커밋 후 비동기로 알림 생성
     * - AFTER_COMMIT: 커밋 성공 후 실행
     * - 알림 실패해도 예약 취소는 이미 DB에 반영됨
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBoothVacancy(BoothVacancyEvent event) {
        vacancyNotificationService.notifyVacancyWithIdempotency(
                event.getBoothId(),
                event.getDisplayName(),        // ← 부스명 사용!
                event.getSlotId(),
                event.getIdempotencyKey()
        );
    }
    }

