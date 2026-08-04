package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TossCancelResponse(
        String paymentKey,
        String orderId,
        BigDecimal totalAmount,
        String status,
        String method,
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt,
        List<Cancel> cancels
) {

    public record Cancel(
            String transactionKey,
            BigDecimal cancelAmount,
            String cancelReason,
            OffsetDateTime canceledAt
    ) {
    }
}
