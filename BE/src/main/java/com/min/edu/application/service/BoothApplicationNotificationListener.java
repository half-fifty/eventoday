package com.min.edu.application.service;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.producer.NotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 부스 신청 승인/반려 결과를 신청자에게 사이트 알림 + 이메일로 발송하는 리스너
 * - AFTER_COMMIT: 승인·반려 트랜잭션이 커밋된 이후에만 발송
 *   (커밋 실패 시 알림이 나가지 않고, 발송 지연이 비관적 락 보유 시간을 늘리지 않음)
 * - EventReviewNotificationListener와 동일한 패턴
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoothApplicationNotificationListener {

    private final NotificationProducer notificationProducer;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyDecision(BoothApplicationDecision decision) {
        String result = decision.approved() ? "승인" : "반려";
        String title = String.format("부스 신청이 %s되었습니다", result);
        String content = decision.approved()
                ? String.format("신청번호 %s 부스 신청이 승인되었습니다.", decision.applicationNo())
                : String.format("신청번호 %s 부스 신청이 반려되었습니다. 사유: %s",
                        decision.applicationNo(), decision.rejectionReason());
        NotificationType type = decision.approved()
                ? NotificationType.BOOTH_APPLICATION_APPROVED
                : NotificationType.BOOTH_APPLICATION_REJECTED;

        // 사이트 알림: 신청서를 제출한 회원에게 발송
        try {
            notificationProducer.send(NotificationEventDto.create(
                    decision.applicantMemberId(),
                    type,
                    "BOOTH_APPLICATION",
                    decision.applicationId(),
                    title,
                    content));
        } catch (RuntimeException exception) {
            log.error("부스 신청 {} 알림 발송 실패 - applicationId: {}", result, decision.applicationId(), exception);
        }

        // 이메일: 신청서에 기재된 담당자 이메일로 발송 (반려 사유 등 자유 입력값은 HTML 이스케이프)
        try {
            emailSender.send(new EmailMessage(
                    decision.contactEmail(),
                    "[EVENTODAY] " + title,
                    "<h2>" + escapeHtml(title) + "</h2><p>" + escapeHtml(content) + "</p>"));
        } catch (RuntimeException exception) {
            log.error("부스 신청 {} 메일 발송 실패 - applicationId: {}", result, decision.applicationId(), exception);
        }
    }

    /** HTML 특수문자 이스케이프 (EventReviewNotificationListener와 동일) */
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
