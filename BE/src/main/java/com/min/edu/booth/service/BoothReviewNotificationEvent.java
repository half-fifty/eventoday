package com.min.edu.booth.service;

import com.min.edu.notification.domain.NotificationType;

// 리뷰 작성자에게 보낼 사이트 알림 이벤트. 트랜잭션 커밋 후에만 실제로 기록되도록
// BoothReviewNotificationListener가 @TransactionalEventListener(AFTER_COMMIT)로 받는다.
public record BoothReviewNotificationEvent(
        Long reviewId,
        Long recipientMemberId,
        NotificationType notificationType,
        String title,
        String content) {
}
