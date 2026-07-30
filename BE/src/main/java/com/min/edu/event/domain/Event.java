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

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

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
}
