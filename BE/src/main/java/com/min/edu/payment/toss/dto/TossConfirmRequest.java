package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;

public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        BigDecimal amount
) {
}
