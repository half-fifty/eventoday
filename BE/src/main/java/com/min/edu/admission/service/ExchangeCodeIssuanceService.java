package com.min.edu.admission.service;

import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class ExchangeCodeIssuanceService {

    private static final String ISSUANCE_EMAIL_SUBJECT =
            "[EVENTODAY] 외부 판매용 입장 코드 발급 완료";

    private final ExchangeCodeIssuanceFinalizer issuanceFinalizer;
    private final ExchangeCodeRequestEmailRecorder emailRecorder;
    private final EmailSender emailSender;

    public ExchangeCodeIssuanceService(
            ExchangeCodeIssuanceFinalizer issuanceFinalizer,
            ExchangeCodeRequestEmailRecorder emailRecorder,
            EmailSender emailSender) {
        this.issuanceFinalizer = issuanceFinalizer;
        this.emailRecorder = emailRecorder;
        this.emailSender = emailSender;
    }

    public ExchangeCodeRequestDtos.IssuanceResponse issue(
            Long requestId,
            AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        ExchangeCodeIssuanceResult result;
        try {
            result = issuanceFinalizer.issue(requestId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED) {
                throw exception;
            }
            // 코드 저장 후 SMTP만 실패한 요청은 다시 코드를 만들지 않고 기존 코드를 재사용한다.
            result = issuanceFinalizer.prepareEmailResend(requestId);
        }
        emailSender.send(new EmailMessage(
            result.recipientEmail(),
            ISSUANCE_EMAIL_SUBJECT,
            buildEmailContent(result)
        ));
        OffsetDateTime emailedAt = emailRecorder.markEmailed(result.requestId());

        return new ExchangeCodeRequestDtos.IssuanceResponse(
            result.requestId(),
            result.eventId(),
            result.status(),
            result.requestedQuantity(),
            result.generatedQuantity(),
            emailedAt
        );
    }

    public ExchangeCodeRequestDtos.EmailResendResponse resendEmail(
            Long requestId,
            AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        ExchangeCodeIssuanceResult result = issuanceFinalizer.prepareEmailResend(requestId);
        emailSender.send(new EmailMessage(
            result.recipientEmail(),
            ISSUANCE_EMAIL_SUBJECT,
            buildEmailContent(result)
        ));
        OffsetDateTime emailedAt = emailRecorder.markEmailed(result.requestId());

        return new ExchangeCodeRequestDtos.EmailResendResponse(
            result.requestId(),
            result.eventId(),
            result.status(),
            result.requestedQuantity(),
            result.generatedQuantity(),
            emailedAt
        );
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String buildEmailContent(ExchangeCodeIssuanceResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("<div style=\"font-family: Arial, sans-serif; color: #222; line-height: 1.6;\">");
        builder.append("<h2>[EVENTODAY] 외부 판매용 입장 코드 발급 완료</h2>");
        builder.append("<p>행사명: ").append(escapeHtml(result.eventName())).append("</p>");
        builder.append("<p>코드 요청 ID: ").append(result.requestId()).append("</p>");
        builder.append("<p>발급 수량: ").append(result.generatedQuantity()).append("</p>");
        builder.append("<p>코드 유효기간: ")
            .append(escapeHtml(formatDateTime(result.expiresAt())))
            .append("</p>");
        builder.append("<p>아래 코드를 안전하게 보관하고 외부에 노출되지 않도록 주의해주세요.</p>");
        builder.append("<pre style=\"padding: 16px; background: #f6f6f6; border: 1px solid #ddd;\">");
        for (String code : result.codes()) {
            builder.append(escapeHtml(code)).append("\n");
        }
        builder.append("</pre>");
        builder.append("</div>");
        return builder.toString();
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return "없음";
        }
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
