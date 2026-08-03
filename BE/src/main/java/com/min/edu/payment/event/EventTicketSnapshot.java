package com.min.edu.payment.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.event.domain.EventStatus;

public record EventTicketSnapshot(
        Long eventId,
        EventStatus status,
        BigDecimal ticketPrice,
        Integer ticketTotalQuantity,
        Integer ticketSoldQuantity,
        Integer ticketPurchaseLimit,
        OffsetDateTime ticketSalesStartAt,
        OffsetDateTime ticketSalesEndAt,
        OffsetDateTime endAt
) {
}
