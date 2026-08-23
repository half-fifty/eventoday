package com.min.edu.payment.outbox.domain;

public enum PaymentOutboxEventType {
    SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL,
    PUBLISH_FUNNEL_COMPLETE_PAYMENT
}
