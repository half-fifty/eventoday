package com.min.edu.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.domain.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentOrderId(Long paymentOrderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.paymentOrderId = :paymentOrderId
        """)
    Optional<Payment> findByPaymentOrderIdForUpdate(
        @Param("paymentOrderId") Long paymentOrderId
    );

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
            p.approvedAt AS approvedAt,
            va.bankCode AS virtualAccountBankCode,
            va.accountNumber AS virtualAccountNumber,
            va.customerName AS virtualAccountCustomerName,
            va.dueAt AS virtualAccountDueAt
        FROM Payment p
        JOIN PaymentOrder po ON po.id = p.paymentOrderId
        JOIN TicketOrder t ON t.paymentOrderId = po.id
        JOIN Event e ON e.id = t.eventId
        LEFT JOIN PaymentVirtualAccount va ON va.paymentId = p.id
        WHERE p.id = :paymentId
            AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
        """)
    Optional<PaymentDetailProjection> findPaymentDetailById(
        @Param("paymentId") Long paymentId
    );

    @Query("""
        SELECT
            p.id AS paymentId,
            p.paymentOrderId AS paymentOrderId,
            p.paymentKey AS paymentKey,
            p.amount AS paymentAmount,
            p.status AS paymentStatus,
            p.method AS paymentMethod,
            po.orderNo AS orderNo,
            po.buyerMemberId AS buyerMemberId,
            po.totalAmount AS totalAmount,
            po.status AS paymentOrderStatus,
            t.id AS ticketOrderId,
            t.eventId AS eventId,
            e.name AS eventName,
            e.startAt AS eventStartAt,
            e.endAt AS eventEndAt,
            t.totalQuantity AS quantity,
            t.status AS ticketOrderStatus
        FROM Payment p
        JOIN PaymentOrder po ON po.id = p.paymentOrderId
        JOIN TicketOrder t ON t.paymentOrderId = po.id
        JOIN Event e ON e.id = t.eventId
        WHERE p.id = :paymentId
            AND po.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
        """)
    Optional<RefundPaymentProjection> findRefundPaymentById(
        @Param("paymentId") Long paymentId
    );
}
