package com.min.edu.ai.dto;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.service.ExchangeCodeOperationQueryService.ExchangeCodeOperationView;
import java.time.OffsetDateTime;

public record ExchangeCodeAiContext(
        Long exchangeCodeId,
        String maskedCode,
        ExchangeCodeStatus status,
        Source source,
        OffsetDateTime expiresAt,
        OffsetDateTime redeemedAt,
        boolean admissionTicketIssued) {

    public static ExchangeCodeAiContext from(ExchangeCodeOperationView view) {
        return new ExchangeCodeAiContext(
            view.exchangeCodeId(),
            view.maskedCode(),
            view.status(),
            Source.valueOf(view.source().name()),
            view.expiresAt(),
            view.redeemedAt(),
            view.admissionTicketIssued()
        );
    }

    public enum Source {
        TICKET_ORDER,
        EXTERNAL_REQUEST
    }
}
