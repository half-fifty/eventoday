package com.min.edu.payment.event;

public interface EventTicketReader {

    EventTicketSnapshot getTicketSnapshot(Long eventId);
}
