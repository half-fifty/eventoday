package com.min.edu.payment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.domain.TicketOrder;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    @Query(
        value = """
            SELECT
                t.id AS ticketOrderId,
                p.orderNo AS orderNo,
                t.eventId AS eventId,
                e.name AS eventName,
                t.totalQuantity AS quantity,
                t.unitPrice AS unitPrice,
                p.totalAmount AS totalAmount,
                p.status AS paymentOrderStatus,
                t.status AS ticketOrderStatus,
                p.expiresAt AS expiresAt,
                t.confirmedAt AS confirmedAt,
                t.createdAt AS createdAt
            FROM TicketOrder t
            JOIN PaymentOrder p ON p.id = t.paymentOrderId
            JOIN Event e ON e.id = t.eventId
            WHERE p.buyerMemberId = :memberId
                AND p.buyerMemberId IS NOT NULL
                AND p.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
            ORDER BY t.createdAt DESC, t.id DESC
            """,
        countQuery = """
            SELECT COUNT(t)
            FROM TicketOrder t
            JOIN PaymentOrder p ON p.id = t.paymentOrderId
            WHERE p.buyerMemberId = :memberId
                AND p.buyerMemberId IS NOT NULL
                AND p.orderType = com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET
            """
    )
    Page<TicketOrderListProjection> findMyTicketOrders(
        @Param("memberId") Long memberId,
        Pageable pageable
    );
}
