package com.min.edu.ai.dto;

import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.service.AdmissionFailureEligibilityView;
import java.time.OffsetDateTime;

public record AdmissionFailureExplanationContext(
        boolean eligible,
        String reasonCode,
        String fallbackMessage,
        String ticketStatus,
        String eventStatus,
        String eventName,
        OffsetDateTime eventEndAt,
        OffsetDateTime usedAt) {

    public static AdmissionFailureExplanationContext from(
            AdmissionFailureEligibilityView view,
            String fallbackMessage) {
        AdmissionEligibilityResult eligibility = view.eligibility();
        return new AdmissionFailureExplanationContext(
            eligibility.eligible(),
            eligibility.reasonCode().name(),
            fallbackMessage,
            eligibility.ticketStatus().name(),
            eligibility.eventStatus().name(),
            view.eventName(),
            eligibility.eventEndAt(),
            eligibility.usedAt()
        );
    }
}
