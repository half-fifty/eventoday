package com.min.edu.payment.service;

import com.min.edu.payment.policy.RefundEligibilityResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundEligibilityView(
        Long paymentId,
        Long ticketOrderId,
        Long eventId,
        String eventName,
        String paymentMethod,
        String refundStatus,
        BigDecimal refundAmount,
        RefundEligibilityResult eligibility,
        OffsetDateTime evaluatedAt) {
}
