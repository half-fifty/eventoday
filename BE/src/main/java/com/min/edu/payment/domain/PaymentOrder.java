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
            .status(status.name())
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

    public void markPaid(OffsetDateTime now) {
        if (!isPending()) {
            throw new IllegalStateException("Payment order is not pending.");
        }

        this.status = PaymentOrderStatus.PAID.name();
        this.updatedAt = now;
    }
}
