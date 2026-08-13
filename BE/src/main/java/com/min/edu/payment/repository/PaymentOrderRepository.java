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
}
