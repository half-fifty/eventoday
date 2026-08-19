package com.min.edu.ai.service;

import com.min.edu.ai.dto.AiCopilotRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CopilotRetrievalQuerySanitizer {

    private static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{10,}\\b"),
        Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
        Pattern.compile("(?i)\\b(?:paymentKey|payment_key|qrToken|qr_token|orderAccessToken|order_access_token|apiKey|api_key|accessToken|access_token|jwt)\\s*[:=]\\s*\\S+"),
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
        Pattern.compile("\\b(?:\\+?82[-\\s]?)?0?1[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b"),
        Pattern.compile("\\b\\d{2,3}[-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b"),
        Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
        Pattern.compile("\\b[A-Za-z0-9_-]{32,}\\b"),
        Pattern.compile("\\b[A-Za-z0-9+/]{32,}={0,2}\\b")
    );

    private static final List<Pattern> RESOURCE_IDENTIFIER_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(?:orderNo|order_no|orderId|order_id|주문번호|주문)\\s*[:=]?\\s*[A-Za-z0-9._-]{4,}\\b"),
        Pattern.compile("(?i)\\b(?:admissionTicketId|admission_ticket_id|ticketId|ticket_id|입장권)\\s*[:=]?\\s*\\d+\\b"),
        Pattern.compile("(?i)\\b(?:exchangeCodeId|exchange_code_id|교환코드)\\s*[:=]?\\s*\\d+\\b")
    );

    public String safeQuestionForPrompt(String question) {
        return sanitizeSensitive(question);
    }

    public String buildRetrievalQuery(
            String question,
            AiCopilotRequest.Context context) {
        String sanitized = sanitizeResourceIdentifiers(sanitizeSensitive(question));
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(sanitized)) {
            parts.add(sanitized);
        }
        parts.addAll(contextHints(context));
        return String.join(" ", parts).trim();
    }

    private String sanitizeSensitive(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String sanitized = value.trim();
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(REDACTED);
        }
        return collapseWhitespace(sanitized);
    }

    private String sanitizeResourceIdentifiers(String value) {
        String sanitized = value;
        for (Pattern pattern : RESOURCE_IDENTIFIER_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(REDACTED);
        }
        return collapseWhitespace(sanitized);
    }

    private List<String> contextHints(AiCopilotRequest.Context context) {
        if (context == null) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        if (StringUtils.hasText(context.orderNo())) {
            hints.add("order context present");
        }
        if (context.admissionTicketId() != null) {
            hints.add("admission ticket context present");
        }
        if (context.exchangeCodeId() != null) {
            hints.add("exchange code context present");
        }
        if ("QR_PROVIDED".equals(context.qrToken())) {
            hints.add("qr context present");
        }
        return hints;
    }

    private String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
