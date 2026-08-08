package com.min.edu.admission.repository;

import com.min.edu.admission.domain.AdmissionTicket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionTicketRepository extends JpaRepository<AdmissionTicket, Long> {

    boolean existsByQrToken(String qrToken);

    boolean existsByExchangeCodeId(Long exchangeCodeId);
}
