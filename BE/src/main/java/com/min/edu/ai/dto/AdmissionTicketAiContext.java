package com.min.edu.ai.dto;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.service.AdmissionTicketOperationQueryService.AdmissionTicketOperationView;
import java.time.OffsetDateTime;

public record AdmissionTicketAiContext(
        Long admissionTicketId,
        Long eventId,
        String eventName,
        AdmissionTicketStatus status,
        boolean qrAvailable,
        OffsetDateTime issuedAt,
        OffsetDateTime usedAt,
        OffsetDateTime cancelledAt,
        ExchangeCodeStatus exchangeCodeStatus) {

    public static AdmissionTicketAiContext from(AdmissionTicketOperationView view) {
        return new AdmissionTicketAiContext(
            view.admissionTicketId(),
            view.eventId(),
            view.eventName(),
            view.status(),
            view.qrAvailable(),
            view.issuedAt(),
            view.usedAt(),
            view.cancelledAt(),
            view.exchangeCodeStatus()
        );
    }
}
