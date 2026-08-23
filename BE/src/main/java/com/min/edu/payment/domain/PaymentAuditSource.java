package com.min.edu.payment.domain;

public enum PaymentAuditSource {
    CONFIRM,
    WEBHOOK,
    RECONCILIATION,
    EXPIRATION,
    REFUND
}
