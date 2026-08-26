package com.min.edu.admission.policy;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class AdmissionEligibilityPolicy {

    public AdmissionEligibilityResult evaluate(
            Long requestedEventId,
            Event event,
            AdmissionTicket ticket,
            ExchangeCode exchangeCode,
            OffsetDateTime now) {
        if (!requestedEventId.equals(exchangeCode.getEventId())) {
            return result(false, AdmissionEligibilityReasonCode.EVENT_MISMATCH, event, ticket);
        }
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return result(false, AdmissionEligibilityReasonCode.EVENT_NOT_PUBLISHED, event, ticket);
        }
        if (!event.getEndAt().isAfter(now)) {
            return result(false, AdmissionEligibilityReasonCode.EVENT_ENDED, event, ticket);
        }
        if (ticket.getStatus() == AdmissionTicketStatus.USED) {
            return result(false, AdmissionEligibilityReasonCode.ALREADY_USED, event, ticket);
        }
        if (ticket.getStatus() != AdmissionTicketStatus.ISSUED) {
            return result(false, AdmissionEligibilityReasonCode.TICKET_NOT_ISSUED, event, ticket);
        }
        return result(true, AdmissionEligibilityReasonCode.ELIGIBLE, event, ticket);
    }

    private AdmissionEligibilityResult result(
            boolean eligible,
            AdmissionEligibilityReasonCode reasonCode,
            Event event,
            AdmissionTicket ticket) {
        return new AdmissionEligibilityResult(
            eligible,
            reasonCode,
            ticket.getStatus(),
            event.getStatus(),
            event.getEndAt(),
            ticket.getUsedAt()
        );
    }
}
