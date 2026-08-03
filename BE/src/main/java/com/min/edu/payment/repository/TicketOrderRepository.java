package com.min.edu.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.payment.domain.TicketOrder;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {
}
