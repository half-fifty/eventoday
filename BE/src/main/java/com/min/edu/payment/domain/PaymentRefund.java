package com.min.edu.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "payment_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "exchange_code_id")
    private Long exchangeCodeId;

    @Column(name = "requester_member_id")
    private Long requesterMemberId;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal refundAmount;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentRefundStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "pg_cancel_key", length = 200)
    private String pgCancelKey;

    public static PaymentRefund requested(
            Long paymentId,
            Long requesterMemberId,
            BigDecimal refundAmount,
            String reason,
            OffsetDateTime requestedAt) {
        return PaymentRefund.builder()
            .paymentId(paymentId)
            .requesterMemberId(requesterMemberId)
            .refundAmount(refundAmount)
            .reason(reason)
            .status(PaymentRefundStatus.REQUESTED)
            .requestedAt(requestedAt)
            .build();
    }

    public boolean isCompleted() {
        return status == PaymentRefundStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == PaymentRefundStatus.FAILED;
    }

    public void complete(String pgCancelKey, OffsetDateTime completedAt) {
        if (status != PaymentRefundStatus.REQUESTED) {
            throw new IllegalStateException("Refund is not requested.");
        }

        this.status = PaymentRefundStatus.COMPLETED;
        this.pgCancelKey = pgCancelKey;
        this.completedAt = completedAt;
    }

    public void fail(OffsetDateTime failedAt) {
        if (status == PaymentRefundStatus.COMPLETED) {
            throw new IllegalStateException("Completed refund cannot fail.");
        }

        this.status = PaymentRefundStatus.FAILED;
        this.completedAt = failedAt;
    }

    public void failAfterGatewayCancellation(String pgCancelKey, OffsetDateTime failedAt) {
        fail(failedAt);
        this.pgCancelKey = pgCancelKey;
    }

    public void retry(
            Long requesterMemberId,
            BigDecimal refundAmount,
            String reason,
            OffsetDateTime requestedAt) {
        if (status != PaymentRefundStatus.FAILED) {
            throw new IllegalStateException("Only failed refund can retry.");
        }

        this.requesterMemberId = requesterMemberId;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.status = PaymentRefundStatus.REQUESTED;
        this.requestedAt = requestedAt;
        this.completedAt = null;
    }
}
