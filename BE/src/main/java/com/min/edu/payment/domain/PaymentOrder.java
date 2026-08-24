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

// status: 명세서에 상태값 목록이 명시되어 있지 않아 String으로 두었습니다.
// 값 목록이 확정되면 Java Enum(PaymentOrderStatus)으로 교체해주세요.
@Entity
@Table(name = "payment_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(name = "buyer_member_id")
    private Long buyerMemberId;

    @Column(name = "buyer_name", length = 50)
    private String buyerName;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 30)
    private String buyerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private PaymentOrderType orderType;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_payment_method", nullable = false, length = 30)
    private PaymentMethod requestedPaymentMethod;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static PaymentOrder createTicketOrder(
            String orderNo,
            Long buyerMemberId,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            BigDecimal totalAmount,
            PaymentMethod requestedPaymentMethod,
            PaymentOrderStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        return PaymentOrder.builder()
            .orderNo(orderNo)
            .buyerMemberId(buyerMemberId)
            .buyerName(buyerName)
            .buyerEmail(buyerEmail)
            .buyerPhone(buyerPhone)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(totalAmount)
            .requestedPaymentMethod(requestedPaymentMethod)
            .status(status.name())
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static PaymentOrder createEventAdOrder(
            String orderNo,
            Long buyerMemberId,
            String buyerName,
            String buyerEmail,
            BigDecimal totalAmount,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        return PaymentOrder.builder()
            .orderNo(orderNo)
            .buyerMemberId(buyerMemberId)
            .buyerName(buyerName)
            .buyerEmail(buyerEmail)
            .orderType(PaymentOrderType.EVENT_AD)
            .totalAmount(totalAmount)
            .requestedPaymentMethod(PaymentMethod.CARD)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public boolean isPaid() {
        return PaymentOrderStatus.PAID.name().equals(status);
    }

    public boolean isPending() {
        return PaymentOrderStatus.PENDING.name().equals(status);
    }

    public boolean isWaitingForDeposit() {
        return PaymentOrderStatus.WAITING_FOR_DEPOSIT.name().equals(status);
    }

    public boolean isExpired() {
        return PaymentOrderStatus.EXPIRED.name().equals(status);
    }

    public boolean isRefunded() {
        return PaymentOrderStatus.REFUNDED.name().equals(status);
    }

    public void selectPaymentMethod(PaymentMethod paymentMethod, OffsetDateTime now) {
        if (!isPending()) {
            throw new IllegalStateException("Payment method can only be changed while the order is pending.");
        }
        if (paymentMethod != PaymentMethod.CARD && paymentMethod != PaymentMethod.VIRTUAL_ACCOUNT) {
            throw new IllegalArgumentException("Unsupported advertisement payment method.");
        }
        this.requestedPaymentMethod = paymentMethod;
        this.updatedAt = now;
    }

    public void markPaid(OffsetDateTime now) {
        if (!isPending()) {
            throw new IllegalStateException("Payment order is not pending.");
        }

        this.status = PaymentOrderStatus.PAID.name();
        this.updatedAt = now;
    }

    public void markWaitingForDeposit(OffsetDateTime now) {
        if (!isPending()) {
            throw new IllegalStateException("Payment order is not pending.");
        }

        this.status = PaymentOrderStatus.WAITING_FOR_DEPOSIT.name();
        this.updatedAt = now;
    }

    public void alignVirtualAccountExpiry(OffsetDateTime dueAt, OffsetDateTime now) {
        if ((!isPending() && !isWaitingForDeposit()) || dueAt == null) {
            throw new IllegalStateException("Virtual account expiry cannot be changed.");
        }
        this.expiresAt = dueAt;
        this.updatedAt = now;
    }

    public void markPaidFromWaiting(OffsetDateTime now) {
        if (!isWaitingForDeposit()) {
            throw new IllegalStateException("Payment order is not waiting for deposit.");
        }

        this.status = PaymentOrderStatus.PAID.name();
        this.updatedAt = now;
    }

    public void expire(OffsetDateTime now) {
        if (!isPending() && !isWaitingForDeposit()) {
            throw new IllegalStateException("Payment order cannot expire.");
        }

        this.status = PaymentOrderStatus.EXPIRED.name();
        this.updatedAt = now;
    }

    public void markRefunded(OffsetDateTime now) {
        if (!isPaid()) {
            throw new IllegalStateException("Payment order is not paid.");
        }

        this.status = PaymentOrderStatus.REFUNDED.name();
        this.updatedAt = now;
    }
}
