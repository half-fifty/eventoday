package com.min.edu.booth.service;

import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// EventReviewNotificationListener와 동일한 패턴: 카프카로 바로 보내는 대신 outbox_events에 기록해두고
// 실제 발행은 OutboxEventPublishRunner가 담당한다. AFTER_COMMIT이라 원본 트랜잭션이 롤백되면
// (예: 답글 저장 실패) 알림도 함께 사라진다.
@Slf4j
@Component
@RequiredArgsConstructor
public class BoothReviewNotificationListener {

    private static final String REFERENCE_TYPE = "BOOTH_REVIEW";

    private final OutboxEventWriter outboxEventWriter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notify(BoothReviewNotificationEvent event) {
        try {
            outboxEventWriter.write(NotificationEventDto.create(
                    event.recipientMemberId(),
                    event.notificationType(),
                    REFERENCE_TYPE,
                    event.reviewId(),
                    event.title(),
                    event.content()));
        } catch (RuntimeException exception) {
            log.error("부스 리뷰 알림 기록 실패 reviewId={}, memberId={}",
                    event.reviewId(), event.recipientMemberId(), exception);
        }
    }
}
