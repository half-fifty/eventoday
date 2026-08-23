package com.min.edu.payment.repository;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.min.edu.payment.domain.PaymentOrder;

import jakarta.persistence.LockModeType;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    boolean existsByOrderNo(String orderNo);

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM PaymentOrder p
        WHERE p.orderNo = :orderNo
        """)
    Optional<PaymentOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM PaymentOrder p
        WHERE p.id = :id
        """)
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT po
        FROM PaymentOrder po
        JOIN Payment p ON p.paymentOrderId = po.id
        JOIN PaymentVirtualAccount va ON va.paymentId = p.id
        WHERE va.id = :virtualAccountId
        """)
    Optional<PaymentOrder> findByVirtualAccountIdForUpdate(
        @Param("virtualAccountId") Long virtualAccountId
    );

    @Query("""
        SELECT p
        FROM PaymentOrder p
        WHERE p.requestedPaymentMethod = com.min.edu.payment.domain.PaymentMethod.VIRTUAL_ACCOUNT
            AND p.status = 'PENDING'
            AND p.expiresAt <= :now
        ORDER BY p.expiresAt ASC, p.id ASC
        """)
    List<PaymentOrder> findExpiredPendingVirtualAccountOrders(
        @Param("now") OffsetDateTime now,
        Pageable pageable
    );

    @Query("""
        SELECT po
        FROM PaymentOrder po
        WHERE po.requestedPaymentMethod = com.min.edu.payment.domain.PaymentMethod.VIRTUAL_ACCOUNT
            AND po.status = 'PENDING'
            AND po.createdAt <= :threshold
            AND NOT EXISTS (
                SELECT 1
                FROM Payment p
                WHERE p.paymentOrderId = po.id
            )
        ORDER BY po.createdAt ASC, po.id ASC
        """)
    List<PaymentOrder> findSuspiciousPendingVirtualAccountOrders(
        @Param("threshold") OffsetDateTime threshold,
        Pageable pageable
    );
}
