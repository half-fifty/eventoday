package com.min.edu.payment.service;

import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditLog;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.repository.PaymentAuditLogRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAuditLogWriter {

    private static final String MEMBER_ACTOR = "MEMBER";

    private final PaymentAuditLogRepository repository;

    public void append(
            Long paymentOrderId,
            Long paymentId,
            Long refundId,
            PaymentAuditEventType eventType,
            String fromStatus,
            String toStatus,
            PaymentAuditSource source,
            String reasonCode,
            Long actorId,
            String requestId,
            OffsetDateTime occurredAt) {
        repository.save(PaymentAuditLog.builder()
            .paymentOrderId(paymentOrderId)
            .paymentId(paymentId)
            .refundId(refundId)
            .eventType(eventType)
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .source(source)
            .reasonCode(reasonCode)
            .actorType(actorType(actorId))
            .actorId(actorId)
            .requestId(requestId)
            .occurredAt(occurredAt == null ? OffsetDateTime.now() : occurredAt)
            .build());
    }

    private String actorType(Long actorId) {
        return actorId == null ? null : MEMBER_ACTOR;
    }
}
