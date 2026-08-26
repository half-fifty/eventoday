package com.min.edu.event.policy;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

@Component
public class EventOperationDeadlinePolicy {

    private static final long OPERATION_CUTOFF_HOURS_BEFORE_END = 1L;

    public OffsetDateTime operationCutoff(OffsetDateTime eventEndAt) {
        return eventEndAt.minusHours(OPERATION_CUTOFF_HOURS_BEFORE_END);
    }

    public boolean isBeforeOperationCutoff(OffsetDateTime now, OffsetDateTime eventEndAt) {
        return now.isBefore(operationCutoff(eventEndAt));
    }

    public OffsetDateTime effectiveTicketSalesEndAt(
            OffsetDateTime ticketSalesEndAt,
            OffsetDateTime eventEndAt) {
        OffsetDateTime cutoff = operationCutoff(eventEndAt);
        if (ticketSalesEndAt == null || ticketSalesEndAt.isAfter(cutoff)) {
            return cutoff;
        }
        return ticketSalesEndAt;
    }
}
