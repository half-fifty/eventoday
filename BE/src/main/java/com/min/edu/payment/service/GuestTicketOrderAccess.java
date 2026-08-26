package com.min.edu.payment.service;

public record GuestTicketOrderAccess(
        Long ticketOrderId,
        Long eventId,
        String orderNo) {
}
