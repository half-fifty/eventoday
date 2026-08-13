package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        BigDecimal totalAmount,
        String status,
        String method,
        String secret,
        VirtualAccount virtualAccount,
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt
) {

    public TossConfirmResponse(
            String paymentKey,
            String orderId,
            BigDecimal totalAmount,
            String status,
            String method,
            OffsetDateTime requestedAt,
            OffsetDateTime approvedAt) {
        this(
            paymentKey,
            orderId,
            totalAmount,
            status,
            method,
            null,
            null,
            requestedAt,
            approvedAt
        );
    }

    public record VirtualAccount(
            String accountNumber,
            String bankCode,
            String customerName,
            OffsetDateTime dueDate
    ) {
    }
}
