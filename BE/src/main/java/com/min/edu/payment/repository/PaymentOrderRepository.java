package com.min.edu.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
