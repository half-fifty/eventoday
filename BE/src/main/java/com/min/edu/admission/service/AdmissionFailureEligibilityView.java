package com.min.edu.admission.service;

import com.min.edu.admission.policy.AdmissionEligibilityResult;

public record AdmissionFailureEligibilityView(
        Long admissionTicketId,
        Long eventId,
        String eventName,
        AdmissionEligibilityResult eligibility) {
}
