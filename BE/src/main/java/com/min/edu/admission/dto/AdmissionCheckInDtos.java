package com.min.edu.admission.dto;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import java.time.OffsetDateTime;

public final class AdmissionCheckInDtos {
    private AdmissionCheckInDtos() {}

    public record CheckInRequest(
            String qrToken,
            String gateName) {}

    public record CheckInResponse(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus status,
            OffsetDateTime usedAt,
            Long admissionLogId,
            AdmissionAction action,
            AdmissionResult result,
            OffsetDateTime processedAt) {}

    public record CheckInCancellationResponse(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus status,
            OffsetDateTime usedAt,
            Long admissionLogId,
            AdmissionAction action,
            AdmissionResult result,
            OffsetDateTime processedAt) {}

    public record LogListResponse(
            Long admissionLogId,
            Long admissionTicketId,
            AdmissionAction action,
            AdmissionResult result,
            String gateName,
            String staffNickname,
            OffsetDateTime processedAt) {}
}
