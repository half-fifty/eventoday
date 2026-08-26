package com.min.edu.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "organizer_organization_id", nullable = false)
    private Long organizerOrganizationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_display_type", nullable = false, length = 30)
    @Builder.Default
    private EventDetailDisplayType detailDisplayType = EventDetailDisplayType.IMAGE_GALLERY;

    @Column(name = "official_website_url", length = 1000)
    private String officialWebsiteUrl;

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", length = 30)
    private RegionCode regionCode;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "ticket_sales_start_at")
    private OffsetDateTime ticketSalesStartAt;

    @Column(name = "ticket_sales_end_at")
    private OffsetDateTime ticketSalesEndAt;

    @Column(name = "ticket_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal ticketPrice;

    @Column(name = "ticket_total_quantity", nullable = false)
    private Integer ticketTotalQuantity;

    @Column(name = "ticket_sold_quantity", nullable = false)
    private Integer ticketSoldQuantity;

    @Column(name = "ticket_purchase_limit", nullable = false)
    private Integer ticketPurchaseLimit;

    @Column(name = "representative_file_id")
    private Long representativeFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventStatus status;

    @Column(name = "booth_recruitment_enabled", nullable = false)
    private boolean boothRecruitmentEnabled;

    @Column(name = "venue_map_enabled", nullable = false)
    private boolean venueMapEnabled;

    @Column(name = "booth_reservation_enabled", nullable = false)
    private boolean boothReservationEnabled;

    @Column(name = "no_show_grace_minutes", nullable = false)
    private Integer noShowGraceMinutes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void update(
            String name, String eventType, String shortDescription, String description,
            EventDetailDisplayType detailDisplayType, String officialWebsiteUrl,
            String venueName, String address, String postalCode, String addressDetail,
            String contactEmail, String contactPhone,
            BigDecimal latitude, BigDecimal longitude, String kakaoPlaceId, RegionCode regionCode,
            OffsetDateTime startAt, OffsetDateTime endAt,
            OffsetDateTime ticketSalesStartAt, OffsetDateTime ticketSalesEndAt,
            BigDecimal ticketPrice, Integer ticketTotalQuantity, Integer ticketPurchaseLimit,
            Long representativeFileId, boolean boothRecruitmentEnabled,
            boolean venueMapEnabled, boolean boothReservationEnabled,
            Integer noShowGraceMinutes, OffsetDateTime now) {
        if (status != EventStatus.PREPARING && status != EventStatus.REJECTED) {
            throw new IllegalStateException("준비 또는 반려 상태의 행사만 수정할 수 있습니다.");
        }
        if (ticketTotalQuantity < ticketSoldQuantity) {
            throw new IllegalStateException("판매된 티켓 수보다 총 티켓 수를 적게 변경할 수 없습니다.");
        }
        this.name = name;
        this.eventType = eventType;
        this.shortDescription = shortDescription;
        this.description = description;
        this.detailDisplayType = detailDisplayType;
        this.officialWebsiteUrl = officialWebsiteUrl;
        this.venueName = venueName;
        this.address = address;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.postalCode = postalCode;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.kakaoPlaceId = kakaoPlaceId;
        this.regionCode = regionCode;
        this.startAt = startAt;
        this.endAt = endAt;
        this.ticketSalesStartAt = ticketSalesStartAt;
        this.ticketSalesEndAt = ticketSalesEndAt;
        this.ticketPrice = ticketPrice;
        this.ticketTotalQuantity = ticketTotalQuantity;
        this.ticketPurchaseLimit = ticketPurchaseLimit;
        this.representativeFileId = representativeFileId;
        this.boothRecruitmentEnabled = boothRecruitmentEnabled;
        this.venueMapEnabled = venueMapEnabled;
        this.boothReservationEnabled = boothReservationEnabled;
        this.noShowGraceMinutes = noShowGraceMinutes;
        this.updatedAt = now;
    }

    public void update(
            String name, String eventType, String shortDescription, String description,
            EventDetailDisplayType detailDisplayType, String officialWebsiteUrl,
            String venueName, String address, String postalCode, String addressDetail,
            String contactEmail, String contactPhone,
            BigDecimal latitude, BigDecimal longitude, String kakaoPlaceId,
            OffsetDateTime startAt, OffsetDateTime endAt,
            OffsetDateTime ticketSalesStartAt, OffsetDateTime ticketSalesEndAt,
            BigDecimal ticketPrice, Integer ticketTotalQuantity, Integer ticketPurchaseLimit,
            Long representativeFileId, boolean boothRecruitmentEnabled,
            boolean venueMapEnabled, boolean boothReservationEnabled,
            Integer noShowGraceMinutes, OffsetDateTime now) {
        update(name, eventType, shortDescription, description, detailDisplayType, officialWebsiteUrl,
                venueName, address, postalCode,
                addressDetail, contactEmail, contactPhone, latitude, longitude, kakaoPlaceId,
                RegionCode.fromAddress(address), startAt, endAt, ticketSalesStartAt,
                ticketSalesEndAt, ticketPrice, ticketTotalQuantity, ticketPurchaseLimit,
                representativeFileId, boothRecruitmentEnabled, venueMapEnabled,
                boothReservationEnabled, noShowGraceMinutes, now);
    }

    public void updateRepresentativeFile(Long representativeFileId, OffsetDateTime now) {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("취소된 행사의 포스터는 수정할 수 없습니다.");
        }
        this.representativeFileId = representativeFileId;
        this.updatedAt = now;
    }

    public void updatePublicInfo(String shortDescription, String description,
            EventDetailDisplayType detailDisplayType, String officialWebsiteUrl,
            String contactEmail, String contactPhone, Long representativeFileId,
            OffsetDateTime now) {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("취소된 행사의 공개 정보는 수정할 수 없습니다.");
        }
        this.shortDescription = shortDescription;
        this.description = description;
        this.detailDisplayType = detailDisplayType;
        this.officialWebsiteUrl = officialWebsiteUrl;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.representativeFileId = representativeFileId;
        this.updatedAt = now;
    }

    public void submit(OffsetDateTime now) {
        requireStatus(EventStatus.PREPARING, EventStatus.REJECTED);
        this.status = EventStatus.SUBMITTED;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void approve(OffsetDateTime now) {
        requireStatus(EventStatus.SUBMITTED, EventStatus.UNDER_REVIEW);
        this.status = EventStatus.APPROVED;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void reject(String reason, OffsetDateTime now) {
        requireStatus(EventStatus.SUBMITTED, EventStatus.UNDER_REVIEW);
        this.status = EventStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }

    public void publish(OffsetDateTime now) {
        requireStatus(EventStatus.APPROVED);
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void suspend(OffsetDateTime now) {
        requireStatus(EventStatus.PUBLISHED);
        this.status = EventStatus.SUSPENDED;
        this.updatedAt = now;
    }

    public void end(OffsetDateTime now) {
        if (status != EventStatus.PUBLISHED && status != EventStatus.SUSPENDED) {
            throw new IllegalStateException("공개 또는 중단 상태의 행사만 종료할 수 있습니다.");
        }
        this.status = EventStatus.ENDED;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        if (status == EventStatus.ENDED || status == EventStatus.CANCELLED) {
            throw new IllegalStateException("종료 또는 취소된 행사는 다시 취소할 수 없습니다.");
        }
        this.status = EventStatus.CANCELLED;
        this.updatedAt = now;
    }

    private void requireStatus(EventStatus... allowed) {
        for (EventStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("현재 행사 상태에서는 요청한 작업을 수행할 수 없습니다.");
    }
}
