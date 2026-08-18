package com.min.edu.ai.dto;

import com.min.edu.payment.service.TicketOrderOperationQueryService.TicketOrderOperationView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TicketOrderAiContext(
        String orderNo,
        String eventName,
        Integer quantity,
        BigDecimal totalAmount,
        String paymentOrderStatus,
        String ticketOrderStatus,
        OffsetDateTime expiresAt,
        OffsetDateTime confirmedAt) {

    public static TicketOrderAiContext from(TicketOrderOperationView response) {
        return new TicketOrderAiContext(
            response.orderNo(),
            response.eventName(),
            response.quantity(),
            response.totalAmount(),
            response.paymentOrderStatus(),
            response.ticketOrderStatus(),
            response.expiresAt(),
            response.confirmedAt()
        );
    }
}
