package com.min.edu.payment.policy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundEligibilityResult(
        boolean refundable,
        RefundEligibilityReasonCode reasonCode,
        String paymentStatus,
        String paymentOrderStatus,
        String ticketOrderStatus,
        BigDecimal refundAmount,
        OffsetDateTime eventEndAt,
        OffsetDateTime operationCutoffAt,
        boolean exchangeCodeRedeemed,
        OffsetDateTime evaluatedAt) {
}
