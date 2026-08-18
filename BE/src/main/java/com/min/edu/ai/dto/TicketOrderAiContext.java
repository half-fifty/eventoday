package com.min.edu.ai.dto;

import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TicketOrderAiContext(
        String orderNo,
        String eventName,
        Integer quantity,
        BigDecimal totalAmount,
        String paymentOrderStatus,
        String ticketOrderStatus,
        OffsetDateTime expiresAt) {

    public static TicketOrderAiContext from(TicketOrderDetailResponse response) {
        return new TicketOrderAiContext(
            response.getOrderNo(),
            response.getEventName(),
            response.getQuantity(),
            response.getTotalAmount(),
            response.getPaymentOrderStatus(),
            response.getTicketOrderStatus(),
            response.getExpiresAt()
        );
    }
}
