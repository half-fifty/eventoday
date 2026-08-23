package com.min.edu.payment.repository;

import com.min.edu.payment.domain.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
}
