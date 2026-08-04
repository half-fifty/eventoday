package com.min.edu.admission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;

public interface ExchangeCodeRepository extends JpaRepository<ExchangeCode, Long> {

    boolean existsByCode(String code);

    List<ExchangeCode> findAllByTicketOrderIdOrderByIdAsc(Long ticketOrderId);

    boolean existsByTicketOrderIdAndStatus(Long ticketOrderId, ExchangeCodeStatus status);
}
