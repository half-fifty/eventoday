package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "booths",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booths_event_booth_code",
                columnNames = {"event_id", "booth_code"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "assigned_organization_id")
    private Long assignedOrganizationId;

    @Column(name = "booth_code", nullable = false, length = 30)
    private String boothCode;

    @Column(name = "booth_type", nullable = false, length = 50)
    private String boothType;

    @Column(name = "floor_name", length = 50)
    private String floorName;

    @Column(name = "zone_name", length = 50)
    private String zoneName;

    @Column(name = "location_description", length = 200)
    private String locationDescription;

    @Column(name = "width_meter", precision = 6, scale = 2)
    private BigDecimal widthMeter;

    @Column(name = "depth_meter", precision = 6, scale = 2)
    private BigDecimal depthMeter;

    @Column(name = "area_sqm", precision = 8, scale = 2)
    private BigDecimal areaSqm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "basic_equipment", columnDefinition = "jsonb")
    private String basicEquipment;

    @Column(name = "electricity_available", nullable = false)
    private boolean electricityAvailable;

    @Column(name = "water_available", nullable = false)
    private boolean waterAvailable;

    @Column(name = "drainage_available", nullable = false)
    private boolean drainageAvailable;

    @Column(name = "internet_available", nullable = false)
    private boolean internetAvailable;

    @Column(name = "price", nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BoothStatus status;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "short_intro", length = 300)
    private String shortIntro;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "exhibition_content", columnDefinition = "TEXT")
    private String exhibitionContent;

    @Column(name = "representative_file_id")
    private Long representativeFileId;

    @Column(name = "qr_token", unique = true, length = 100)
    private String qrToken;

    @Column(name = "qr_issued_at")
    private OffsetDateTime qrIssuedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 부스 신청 접수 시 상태를 APPLICATION_PENDING으로 변경
     * updatedAt도 함께 갱신
     */
    public void markAsPending(OffsetDateTime now) {
        this.status = BoothStatus.APPLICATION_PENDING;
        this.updatedAt = now;
    }
}
