package com.min.edu.admission.policy;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;

public record AdmissionEligibilityResult(
        boolean eligible,
        AdmissionEligibilityReasonCode reasonCode,
        AdmissionTicketStatus ticketStatus,
        EventStatus eventStatus,
        OffsetDateTime eventEndAt,
        OffsetDateTime usedAt) {
}
