package com.min.edu.payment.outbox.domain;

public enum PaymentOutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
