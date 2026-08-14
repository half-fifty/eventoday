package com.min.edu.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// pg_provider, method, status: 명세서에 닫힌 값 목록이 없어(TOSS_PAYMENTS 외 확장 가능) String으로 두었습니다.
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_order_id", nullable = false)
    private Long paymentOrderId;

    @Column(name = "pg_provider", nullable = false, length = 30)
    private String pgProvider;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(name = "method", length = 30)
    private String method;

    @Column(name = "amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Payment approved(
            Long paymentOrderId,
            String pgProvider,
            String paymentKey,
            String method,
            BigDecimal amount,
            OffsetDateTime requestedAt,
            OffsetDateTime approvedAt,
            OffsetDateTime now) {
        return Payment.builder()
            .paymentOrderId(paymentOrderId)
            .pgProvider(pgProvider)
            .paymentKey(paymentKey)
            .method(method)
            .amount(amount)
            .status(PaymentStatus.PAID.name())
            .requestedAt(requestedAt)
            .approvedAt(approvedAt)
            .updatedAt(now)
            .build();
    }

    public static Payment waitingForDeposit(
            Long paymentOrderId,
            String pgProvider,
            String paymentKey,
            String method,
            BigDecimal amount,
            OffsetDateTime requestedAt,
            OffsetDateTime now) {
        return Payment.builder()
            .paymentOrderId(paymentOrderId)
            .pgProvider(pgProvider)
            .paymentKey(paymentKey)
            .method(method)
            .amount(amount)
            .status(PaymentStatus.WAITING_FOR_DEPOSIT.name())
            .requestedAt(requestedAt)
            .updatedAt(now)
            .build();
    }

    public boolean isPaid() {
        return PaymentStatus.PAID.name().equals(status);
    }

    public boolean isWaitingForDeposit() {
        return PaymentStatus.WAITING_FOR_DEPOSIT.name().equals(status);
    }

    public boolean isExpired() {
        return PaymentStatus.EXPIRED.name().equals(status);
    }

    public boolean isRefunded() {
        return PaymentStatus.REFUNDED.name().equals(status);
    }

    public void markPaid(
            String method,
            OffsetDateTime requestedAt,
            OffsetDateTime approvedAt,
            OffsetDateTime now) {
        if (!isWaitingForDeposit()) {
            throw new IllegalStateException("Payment is not waiting for deposit.");
        }

        this.method = method;
        this.status = PaymentStatus.PAID.name();
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.updatedAt = now;
    }

    public void expire(OffsetDateTime now) {
        if (!isWaitingForDeposit()) {
            throw new IllegalStateException("Payment is not waiting for deposit.");
        }

        this.status = PaymentStatus.EXPIRED.name();
        this.updatedAt = now;
    }

    public void markRefunded(OffsetDateTime now) {
        if (!isPaid()) {
            throw new IllegalStateException("Payment is not paid.");
        }

        this.status = PaymentStatus.REFUNDED.name();
        this.updatedAt = now;
    }
}
