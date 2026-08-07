package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record ExchangeCodeIssuanceResult(
        Long requestId,
        Long eventId,
        String eventName,
        Integer requestedQuantity,
        Integer generatedQuantity,
        String recipientEmail,
        List<String> codes,
        OffsetDateTime expiresAt,
        ExchangeCodeRequestStatus status,
        OffsetDateTime emailedAt) {
}
