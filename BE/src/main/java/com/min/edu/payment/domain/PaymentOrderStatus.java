package com.min.edu.payment.domain;

public enum PaymentOrderStatus {
    PENDING,
    WAITING_FOR_DEPOSIT,
    EXPIRED,
    PAID,
    REFUNDED
}
