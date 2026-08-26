package com.min.edu.advertisement.dto;

import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import com.min.edu.payment.domain.PaymentMethod;

public final class AdvertisementDtos {
    private AdvertisementDtos() {}

    public record SaveRequest(
            @NotNull Long applicantOrganizationId,
            @NotNull Long bannerFileId,
            @Size(max = 300) String adText,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt) {}
    public record UpdateRequest(@NotNull Long bannerFileId, @Size(max = 300) String adText,
            @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {}
    public record CreativeUpdateRequest(@NotNull Long bannerFileId,
            @Size(max = 300) String adText) {}
    public record RejectionRequest(@NotBlank @Size(max = 2000) String reason) {}
    public record PaymentMethodRequest(@NotNull PaymentMethod paymentMethod) {}
    public record VirtualAccountSummary(String bankCode, String accountNumber,
            String customerName, OffsetDateTime dueAt, OffsetDateTime depositedAt) {}
    public record PaymentOrderSummary(String orderNo, BigDecimal totalAmount, String status,
            VirtualAccountSummary virtualAccount) {
        public PaymentOrderSummary(String orderNo, BigDecimal totalAmount, String status) {
            this(orderNo, totalAmount, status, null);
        }
    }
    public record CancellationResponse(String action, AdvertisementStatus advertisementStatus,
            boolean refunded, String message) {}
    public record PricingResponse(BigDecimal eventAdPrice, BigDecimal boothAdPrice,
            long paymentExpiresInMinutes) {}
    public record Response(Long id, Long eventId, Long boothId, Long applicantOrganizationId,
            Long paymentOrderId, Long bannerFileId, String adText, OffsetDateTime startAt,
            OffsetDateTime endAt, AdvertisementStatus status, Long reviewedBy,
            String rejectionReason, OffsetDateTime approvedAt, PaymentOrderSummary paymentOrder) {
        public static Response from(Advertisement ad) {
            return from(ad, null);
        }
        public static Response from(Advertisement ad, PaymentOrderSummary paymentOrder) {
            return new Response(ad.getId(), ad.getEventId(), ad.getBoothId(),
                    ad.getApplicantOrganizationId(), ad.getPaymentOrderId(), ad.getBannerFileId(),
                    ad.getAdText(), ad.getStartAt(), ad.getEndAt(), ad.getStatus(), ad.getReviewedBy(),
                    ad.getRejectionReason(), ad.getApprovedAt(), paymentOrder);
        }
    }
    public record BoothCandidate(Long boothId, String boothCode, String displayName,
            Long assignedOrganizationId) {}
    public record CopySuggestionRequest(@NotNull Long organizationId, @NotNull Long eventId,
            @Size(max = 300) String draft, @Size(max = 30) String tone) {}
    public record CopySuggestion(String copy, String rationale, List<String> warnings) {}
    public record CopySuggestionResponse(List<CopySuggestion> suggestions,
            List<String> sources, String notice) {}
}
