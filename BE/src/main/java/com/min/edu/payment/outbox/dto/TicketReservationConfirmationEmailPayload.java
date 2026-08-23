package com.min.edu.payment.outbox.dto;

public record TicketReservationConfirmationEmailPayload(
        String orderNo,
        String buyerEmail,
        String eventName
) {
}
