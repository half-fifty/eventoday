package com.min.edu.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentOrderId(Long paymentOrderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    long countByPaymentKey(String paymentKey);

    boolean existsByPaymentKeyAndPaymentOrderIdNot(String paymentKey, Long paymentOrderId);

    @Query("""
        SELECT
            p.id AS paymentId,
            po.id AS paymentOrderId,
            po.orderNo AS orderNo,
            po.buyerMemberId AS buyerMemberId,
            t.id AS ticketOrderId,
            t.eventId AS eventId,
            e.name AS eventName,
            p.pgProvider AS pgProvider,
            p.method AS method,
            p.amount AS amount,
            p.status AS paymentStatus,
            t.status AS ticketOrderStatus,
            p.requestedAt AS requestedAt,
            p.approvedAt AS approvedAt
        FROM Payment p
        JOIN PaymentOrder po ON po.id = p.paymentOrderId
        JOIN TicketOrder t ON t.paymentOrderId = po.id
        JOIN Event e ON e.id = t.eventId
        WHERE p.id = :paymentId
            AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
        """)
    Optional<PaymentDetailProjection> findPaymentDetailById(
        @Param("paymentId") Long paymentId
    );
}
