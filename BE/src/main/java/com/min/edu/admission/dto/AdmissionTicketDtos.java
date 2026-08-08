package com.min.edu.admission.dto;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import java.time.OffsetDateTime;

public final class AdmissionTicketDtos {
    private AdmissionTicketDtos() {}

    public record MyListResponse(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus status,
            OffsetDateTime issuedAt,
            OffsetDateTime usedAt,
            OffsetDateTime cancelledAt) {}

    public record DetailResponse(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            ExchangeCodeStatus exchangeCodeStatus,
            AdmissionTicketStatus admissionTicketStatus,
            OffsetDateTime issuedAt,
            OffsetDateTime usedAt,
            OffsetDateTime cancelledAt,
            boolean qrAvailable) {}

    public record EventListResponse(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            String memberNickname,
            AdmissionTicketStatus status,
            OffsetDateTime issuedAt,
            OffsetDateTime usedAt,
            OffsetDateTime cancelledAt) {}
}
