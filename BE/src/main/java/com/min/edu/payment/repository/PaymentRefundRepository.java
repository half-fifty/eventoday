package com.min.edu.payment.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.PaymentRefundStatus;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByPaymentId(Long paymentId);

    boolean existsByPaymentIdAndStatus(Long paymentId, PaymentRefundStatus status);

    @Query("""
        SELECT
            r.id AS refundId,
            r.paymentId AS paymentId,
            p.paymentOrderId AS paymentOrderId,
            po.orderNo AS orderNo,
            po.buyerMemberId AS buyerMemberId,
            t.id AS ticketOrderId,
            t.eventId AS eventId,
            e.name AS eventName,
            r.refundAmount AS refundAmount,
            r.reason AS refundReason,
            r.status AS refundStatus,
            r.requestedAt AS requestedAt,
            r.completedAt AS completedAt,
            p.method AS paymentMethod,
            t.status AS ticketOrderStatus
        FROM PaymentRefund r
        JOIN Payment p ON p.id = r.paymentId
        JOIN PaymentOrder po ON po.id = p.paymentOrderId
        JOIN TicketOrder t ON t.paymentOrderId = po.id
        JOIN Event e ON e.id = t.eventId
        WHERE r.id = :refundId
            AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
        """)
    Optional<RefundDetailProjection> findRefundDetailById(@Param("refundId") Long refundId);

    @Query(
        value = """
            SELECT
                r.id AS refundId,
                r.paymentId AS paymentId,
                po.orderNo AS orderNo,
                t.eventId AS eventId,
                e.name AS eventName,
                r.refundAmount AS refundAmount,
                r.reason AS refundReason,
                r.status AS refundStatus,
                r.requestedAt AS requestedAt,
                r.completedAt AS completedAt
            FROM PaymentRefund r
            JOIN Payment p ON p.id = r.paymentId
            JOIN PaymentOrder po ON po.id = p.paymentOrderId
            JOIN TicketOrder t ON t.paymentOrderId = po.id
            JOIN Event e ON e.id = t.eventId
            WHERE po.buyerMemberId = :memberId
                AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
            ORDER BY r.requestedAt DESC, r.id DESC
            """,
        countQuery = """
            SELECT COUNT(r)
            FROM PaymentRefund r
            JOIN Payment p ON p.id = r.paymentId
            JOIN PaymentOrder po ON po.id = p.paymentOrderId
            JOIN TicketOrder t ON t.paymentOrderId = po.id
            JOIN Event e ON e.id = t.eventId
            WHERE po.buyerMemberId = :memberId
                AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
            """
    )
    Page<RefundListProjection> findMyRefunds(
        @Param("memberId") Long memberId,
        Pageable pageable
    );
}
