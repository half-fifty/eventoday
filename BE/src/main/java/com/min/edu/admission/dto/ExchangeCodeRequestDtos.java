package com.min.edu.admission.dto;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class ExchangeCodeRequestDtos {
    private ExchangeCodeRequestDtos() {}

    public record CreateRequest(
            @NotNull @Min(1) @Max(1000) Integer requestedQuantity,
            @NotBlank @Size(max = 500) String purpose) {}

    public record RejectionRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record IssuanceResponse(
            Long requestId,
            Long eventId,
            ExchangeCodeRequestStatus status,
            Integer requestedQuantity,
            Integer generatedQuantity,
            OffsetDateTime emailedAt) {}

    public record EmailResendResponse(
            Long requestId,
            Long eventId,
            ExchangeCodeRequestStatus status,
            Integer requestedQuantity,
            Integer codeCount,
            OffsetDateTime emailedAt) {}

    public record CreateResponse(
            Long requestId,
            Long eventId,
            Long requestedBy,
            Integer requestedQuantity,
            String purpose,
            ExchangeCodeRequestStatus status,
            OffsetDateTime createdAt) {
        public static CreateResponse from(ExchangeCodeRequest request) {
            return new CreateResponse(
                request.getId(),
                request.getEventId(),
                request.getRequestedBy(),
                request.getRequestedQuantity(),
                request.getPurpose(),
                request.getStatus(),
                request.getCreatedAt()
            );
        }
    }

    public record Response(
            Long requestId,
            Long eventId,
            String eventName,
            Long requestedBy,
            String requesterNickname,
            Integer requestedQuantity,
            String purpose,
            ExchangeCodeRequestStatus status,
            Long reviewedBy,
            OffsetDateTime reviewedAt,
            String rejectionReason,
            OffsetDateTime emailedAt,
            OffsetDateTime createdAt) {
        public static Response from(ExchangeCodeRequestView view) {
            return new Response(
                view.getRequestId(),
                view.getEventId(),
                view.getEventName(),
                view.getRequestedBy(),
                view.getRequesterNickname(),
                view.getRequestedQuantity(),
                view.getPurpose(),
                view.getStatus(),
                view.getReviewedBy(),
                view.getReviewedAt(),
                view.getRejectionReason(),
                view.getEmailedAt(),
                view.getCreatedAt()
            );
        }
    }
}
