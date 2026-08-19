package com.min.edu.ai.dto;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;

public record AdmissionEligibilityAiContext(
        boolean eligible,
        AdmissionEligibilityReasonCode reasonCode,
        AdmissionTicketStatus ticketStatus,
        EventStatus eventStatus,
        OffsetDateTime eventEndAt,
        OffsetDateTime usedAt) {

    public static AdmissionEligibilityAiContext from(AdmissionEligibilityResult result) {
        return new AdmissionEligibilityAiContext(
            result.eligible(),
            result.reasonCode(),
            result.ticketStatus(),
            result.eventStatus(),
            result.eventEndAt(),
            result.usedAt()
        );
    }
}
