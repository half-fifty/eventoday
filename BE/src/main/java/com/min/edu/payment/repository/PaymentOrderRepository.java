package com.min.edu.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.payment.domain.PaymentOrder;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    boolean existsByOrderNo(String orderNo);
}
