package com.min.edu.event.dto;

import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.organization.domain.Organization;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class EventDtos {
    private EventDtos() {}

    public record SaveRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 30) String eventType,
            @Size(max = 300) String shortDescription,
            @NotBlank String description,
            @NotBlank @Size(max = 200) String venueName,
            @NotBlank @Size(max = 300) String address,
            @Size(max = 10) String postalCode,
            @Size(max = 200) String addressDetail,
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 50) String kakaoPlaceId,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            OffsetDateTime ticketSalesStartAt,
            OffsetDateTime ticketSalesEndAt,
            @NotNull @DecimalMin("0") BigDecimal ticketPrice,
            @NotNull @Min(0) Integer ticketTotalQuantity,
            @NotNull @Min(1) Integer ticketPurchaseLimit,
            Long representativeFileId,
            boolean boothRecruitmentEnabled,
            boolean venueMapEnabled,
            boolean boothReservationEnabled,
            @NotNull @Min(0) Integer noShowGraceMinutes) {}

    public record Summary(
            Long id, String name, String eventType, String shortDescription,
            String venueName, String address, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, OffsetDateTime startAt,
            OffsetDateTime endAt, BigDecimal ticketPrice, EventStatus status,
            boolean boothRecruitmentEnabled) {
        public static Summary from(Event event) {
            return new Summary(event.getId(), event.getName(), event.getEventType(),
                    event.getShortDescription(), event.getVenueName(), event.getAddress(),
                    event.getAddressDetail(), event.getLatitude(), event.getLongitude(),
                    event.getStartAt(), event.getEndAt(), event.getTicketPrice(),
                    event.getStatus(), event.isBoothRecruitmentEnabled());
        }
    }

    public record PublicDetail(
            Long id, String name, String eventType, String shortDescription, String description,
            String venueName, String address, String postalCode, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String kakaoPlaceId,
            OffsetDateTime startAt, OffsetDateTime endAt,
            OffsetDateTime ticketSalesStartAt, OffsetDateTime ticketSalesEndAt,
            BigDecimal ticketPrice, Integer ticketPurchaseLimit, Long representativeFileId,
            boolean boothRecruitmentEnabled, boolean venueMapEnabled,
            boolean boothReservationEnabled) {
        public static PublicDetail from(Event event) {
            return new PublicDetail(event.getId(), event.getName(), event.getEventType(),
                    event.getShortDescription(), event.getDescription(), event.getVenueName(),
                    event.getAddress(), event.getPostalCode(), event.getAddressDetail(),
                    event.getLatitude(), event.getLongitude(), event.getKakaoPlaceId(),
                    event.getStartAt(), event.getEndAt(),
                    event.getTicketSalesStartAt(), event.getTicketSalesEndAt(), event.getTicketPrice(),
                    event.getTicketPurchaseLimit(), event.getRepresentativeFileId(),
                    event.isBoothRecruitmentEnabled(), event.isVenueMapEnabled(),
                    event.isBoothReservationEnabled());
        }
    }

    public record Detail(
            Long id, Long organizerOrganizationId, String name, String eventType,
            String shortDescription, String description, String venueName, String address,
            String postalCode, String addressDetail, BigDecimal latitude, BigDecimal longitude,
            String kakaoPlaceId,
            OffsetDateTime startAt, OffsetDateTime endAt,
            OffsetDateTime ticketSalesStartAt, OffsetDateTime ticketSalesEndAt,
            BigDecimal ticketPrice, Integer ticketTotalQuantity, Integer ticketSoldQuantity,
            Integer ticketPurchaseLimit, Long representativeFileId, EventStatus status,
            boolean boothRecruitmentEnabled, boolean venueMapEnabled,
            boolean boothReservationEnabled, Integer noShowGraceMinutes,
            String rejectionReason, OffsetDateTime publishedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static Detail from(Event event) {
            return new Detail(event.getId(), event.getOrganizerOrganizationId(), event.getName(),
                    event.getEventType(), event.getShortDescription(), event.getDescription(),
                    event.getVenueName(), event.getAddress(), event.getPostalCode(),
                    event.getAddressDetail(), event.getLatitude(), event.getLongitude(),
                    event.getKakaoPlaceId(), event.getStartAt(), event.getEndAt(),
                    event.getTicketSalesStartAt(), event.getTicketSalesEndAt(), event.getTicketPrice(),
                    event.getTicketTotalQuantity(), event.getTicketSoldQuantity(),
                    event.getTicketPurchaseLimit(), event.getRepresentativeFileId(), event.getStatus(),
                    event.isBoothRecruitmentEnabled(), event.isVenueMapEnabled(),
                    event.isBoothReservationEnabled(), event.getNoShowGraceMinutes(),
                    event.getRejectionReason(), event.getPublishedAt(), event.getCreatedAt(),
                    event.getUpdatedAt());
        }
    }

    public record RejectionRequest(@NotBlank @Size(max = 2000) String reason) {}
    public record ManagedOrganization(Long id, String name) {
        public static ManagedOrganization from(Organization organization) {
            return new ManagedOrganization(organization.getId(), organization.getName());
        }
    }
    public record MemberRequest(@NotNull Long memberId, @NotNull EventRole eventRole) {}
    public record MemberUpdateRequest(@NotNull EventRole eventRole, boolean active) {}
    public record MemberResponse(Long memberId, EventRole eventRole, boolean active, OffsetDateTime createdAt) {
        public static MemberResponse from(EventMember member) {
            return new MemberResponse(member.getMemberId(), member.getEventRole(),
                    member.isActive(), member.getCreatedAt());
        }
    }
}
