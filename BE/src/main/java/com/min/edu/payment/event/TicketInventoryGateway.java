package com.min.edu.payment.event;

public interface TicketInventoryGateway {

    boolean reserve(Long eventId, int quantity);

    boolean release(Long eventId, int quantity);
}
