package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        BigDecimal totalAmount,
        String status,
        String method,
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt
) {
}
