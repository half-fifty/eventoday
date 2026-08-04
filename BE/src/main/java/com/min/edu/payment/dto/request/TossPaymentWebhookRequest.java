package com.min.edu.payment.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossPaymentWebhookRequest {

    @NotBlank
    private String eventType;

    @NotBlank
    private String createdAt;

    @Valid
    @NotNull
    private PaymentData data;

    public TossPaymentWebhookRequest(
            String eventType,
            String createdAt,
            PaymentData data) {
        this.eventType = eventType;
        this.createdAt = createdAt;
        this.data = data;
    }

    @Getter
    @NoArgsConstructor
    public static class PaymentData {

        @NotBlank
        private String paymentKey;

        @NotBlank
        private String orderId;

        @NotNull
        private BigDecimal totalAmount;

        @NotBlank
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
