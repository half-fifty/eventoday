package com.min.edu.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentOrderId(Long paymentOrderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    long countByPaymentKey(String paymentKey);

    boolean existsByPaymentKeyAndPaymentOrderIdNot(String paymentKey, Long paymentOrderId);
}
