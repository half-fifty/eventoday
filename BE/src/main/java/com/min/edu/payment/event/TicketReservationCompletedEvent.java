package com.min.edu.payment.event;

public record TicketReservationCompletedEvent(
        String orderNo,
        String buyerEmail,
        String eventName
) {
}
