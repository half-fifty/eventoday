package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            LocalDateTime dueDate
    ) {

        @Override
        public String toString() {
            return "VirtualAccount[accountNumber=***, bankCode="
                + bankCode
                + ", customerName=***, dueDate="
                + dueDate
                + "]";
        }
    }

    @Override
    public String toString() {
        return "TossConfirmResponse[paymentKey="
            + paymentKey
            + ", orderId="
            + orderId
            + ", totalAmount="
            + totalAmount
            + ", status="
            + status
            + ", method="
            + method
            + ", secret=***, virtualAccount="
            + (virtualAccount == null ? null : "[MASKED]")
            + ", requestedAt="
            + requestedAt
            + ", approvedAt="
            + approvedAt
            + "]";
    }
}
