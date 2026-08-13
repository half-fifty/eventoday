package com.min.edu.payment.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossPaymentWebhookRequest {

    private String eventType;

    private String createdAt;

    @Valid
    private PaymentData data;

    private String secret;

    private String status;

    private String transactionKey;

    private String orderId;

    public TossPaymentWebhookRequest(
            String eventType,
            String createdAt,
            PaymentData data) {
        this.eventType = eventType;
        this.createdAt = createdAt;
        this.data = data;
    }

    public TossPaymentWebhookRequest(
            String createdAt,
            String secret,
            String status,
            String transactionKey,
            String orderId) {
        this.createdAt = createdAt;
        this.secret = secret;
        this.status = status;
        this.transactionKey = transactionKey;
        this.orderId = orderId;
    }

    @Getter
    @NoArgsConstructor
    public static class PaymentData {

        private String paymentKey;

        private String orderId;

        private BigDecimal totalAmount;

        private String status;

        private String method;

        private OffsetDateTime requestedAt;

        private OffsetDateTime approvedAt;

        public PaymentData(
                String paymentKey,
                String orderId,
                BigDecimal totalAmount,
                String status,
                String method,
                OffsetDateTime requestedAt,
                OffsetDateTime approvedAt) {
            this.paymentKey = paymentKey;
            this.orderId = orderId;
            this.totalAmount = totalAmount;
            this.status = status;
            this.method = method;
            this.requestedAt = requestedAt;
            this.approvedAt = approvedAt;
        }
    }
}
