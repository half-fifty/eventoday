package com.min.edu.admission.dto;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public final class ExchangeCodeRedemptionDtos {
    private ExchangeCodeRedemptionDtos() {}

    public record Request(@NotBlank String code) {}

    public record ValidationResponse(
            boolean valid,
            Long eventId,
            String eventName,
            ExchangeCodeDtos.Source source,
            ExchangeCodeStatus status,
            OffsetDateTime expiresAt) {}

    public record RedemptionResponse(
            Long exchangeCodeId,
            ExchangeCodeStatus exchangeCodeStatus,
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus admissionTicketStatus,
            OffsetDateTime issuedAt,
            boolean qrAvailable) {}
}
