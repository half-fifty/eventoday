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

// status: 명세서에 상태값 목록이 명시되어 있지 않아 String으로 두었습니다.
@Entity
@Table(name = "ticket_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TicketOrder {

    private static final int FUNNEL_SESSION_ID_MAX_LENGTH = 80;
    private static final int FUNNEL_ANONYMOUS_ID_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_order_id", nullable = false, unique = true)
    private Long paymentOrderId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    // 결제 확정 시점(PaymentFinalizer)에 COMPLETE_PAYMENT 퍼널 이벤트를 발행하기 위해
    // 주문 생성 시점의 퍼널 세션 정보를 보관해둔다 (분석용, 없어도 주문 자체엔 영향 없음).
    @Column(name = "funnel_session_id", length = 80)
    private String funnelSessionId;

    @Column(name = "funnel_anonymous_id", length = 100)
    private String funnelAnonymousId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static TicketOrder create(
            Long paymentOrderId,
            Long eventId,
            BigDecimal unitPrice,
            Integer totalQuantity,
            TicketOrderStatus status,
            OffsetDateTime confirmedAt,
            String funnelSessionId,
            String funnelAnonymousId,
            OffsetDateTime now) {
        return TicketOrder.builder()
            .paymentOrderId(paymentOrderId)
            .eventId(eventId)
            .unitPrice(unitPrice)
            .totalQuantity(totalQuantity)
            .status(status.name())
            .confirmedAt(confirmedAt)
            .funnelSessionId(sanitizeFunnelValue(funnelSessionId, FUNNEL_SESSION_ID_MAX_LENGTH))
            .funnelAnonymousId(sanitizeFunnelValue(funnelAnonymousId, FUNNEL_ANONYMOUS_ID_MAX_LENGTH))
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    // 분석용 값이라 형식이 안 맞아도 주문 생성 자체를 막으면 안 된다 — 컬럼 길이를 넘으면
    // INSERT 실패로 주문 전체가 롤백되는 대신 조용히 버린다.
    private static String sanitizeFunnelValue(String value, int maxLength) {
        return (value != null && value.length() > maxLength) ? null : value;
    }

    public boolean isPendingPayment() {
        return TicketOrderStatus.PENDING_PAYMENT.name().equals(status);
    }

    public boolean isConfirmed() {
        return TicketOrderStatus.CONFIRMED.name().equals(status);
    }

    public boolean isRefunded() {
        return TicketOrderStatus.REFUNDED.name().equals(status);
    }

    public boolean isExpired() {
        return TicketOrderStatus.EXPIRED.name().equals(status);
    }

    public void confirm(OffsetDateTime approvedAt, OffsetDateTime updatedAt) {
        if (!isPendingPayment()) {
            throw new IllegalStateException("Ticket order is not pending payment.");
        }

        this.status = TicketOrderStatus.CONFIRMED.name();
        this.confirmedAt = approvedAt;
        this.updatedAt = updatedAt;
    }

    public void expire(OffsetDateTime now) {
        if (!isPendingPayment()) {
            throw new IllegalStateException("Ticket order is not pending payment.");
        }

        this.status = TicketOrderStatus.EXPIRED.name();
        this.updatedAt = now;
    }

    public void refund(OffsetDateTime now) {
        if (!isConfirmed()) {
            throw new IllegalStateException("Ticket order is not confirmed.");
        }

        this.status = TicketOrderStatus.REFUNDED.name();
        this.updatedAt = now;
    }
}
