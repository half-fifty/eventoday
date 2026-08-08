package com.min.edu.event.service;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventOrganizationRepository;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationEventDto;
import com.min.edu.notification.producer.NotificationProducer;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j @Component @RequiredArgsConstructor
public class EventReviewNotificationListener {
    private static final List<OrganizationRole> RECIPIENT_ROLES = List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);
    private final EventOrganizationMemberRepository memberRepository;
    private final EventOrganizationRepository organizationRepository;
    private final NotificationProducer notificationProducer;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyReviewDecision(EventReviewDecision decision) {
        String result = decision.approved() ? "승인" : "반려";
        String title = String.format("행사 등록이 %s되었습니다", result);
        String content = decision.approved() ? String.format("'%s' 행사가 승인되었습니다.", decision.eventName())
                : String.format("'%s' 행사가 반려되었습니다. 사유: %s", decision.eventName(), decision.reason());
        NotificationType type = decision.approved() ? NotificationType.EVENT_APPROVED : NotificationType.EVENT_REJECTED;
        memberRepository.findAllByOrganizationIdAndStatusAndOrganizationRoleIn(decision.organizationId(), OrganizationMemberStatus.ACTIVE, RECIPIENT_ROLES)
                .forEach(member -> sendSiteNotification(member.getMemberId(), type, decision.eventId(),
                        decision.organizationId(), title, content));
        organizationRepository.findById(decision.organizationId()).ifPresent(organization -> {
            try { emailSender.send(new EmailMessage(organization.getContactEmail(), "[EVENTODAY] " + title,
                    "<h2>" + escapeHtml(title) + "</h2><p>" + escapeHtml(content) + "</p>")); }
            catch (RuntimeException exception) { log.error("행사 심사 결과 메일 발송 실패 eventId={}", decision.eventId(), exception); }
        });
    }

    private void sendSiteNotification(Long memberId, NotificationType type, Long eventId, Long organizationId,
            String title, String content) {
        try { notificationProducer.send(NotificationEventDto.create(memberId, type,
                "EVR:" + organizationId, eventId, title, content)); }
        catch (RuntimeException exception) { log.error("행사 심사 결과 사이트 알림 발송 실패 eventId={}, memberId={}", eventId, memberId, exception); }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
