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

    public static Booth create(
            Long eventId,
            String boothCode,
            String boothType,
            String floorName,
            String zoneName,
            String locationDescription,
            BigDecimal widthMeter,
            BigDecimal depthMeter,
            BigDecimal areaSqm,
            String basicEquipment,
            boolean electricityAvailable,
            boolean waterAvailable,
            boolean drainageAvailable,
            boolean internetAvailable,
            BigDecimal price,
            OffsetDateTime now) {
        return Booth.builder()
                .eventId(eventId)
                .boothCode(boothCode)
                .boothType(boothType)
                .floorName(floorName)
                .zoneName(zoneName)
                .locationDescription(locationDescription)
                .widthMeter(widthMeter)
                .depthMeter(depthMeter)
                .areaSqm(areaSqm)
                .basicEquipment(basicEquipment)
                .electricityAvailable(electricityAvailable)
                .waterAvailable(waterAvailable)
                .drainageAvailable(drainageAvailable)
                .internetAvailable(internetAvailable)
                .price(price)
                .status(BoothStatus.AVAILABLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void updateDetails(
            String boothCode,
            String boothType,
            String floorName,
            String zoneName,
            String locationDescription,
            BigDecimal widthMeter,
            BigDecimal depthMeter,
            BigDecimal areaSqm,
            String basicEquipment,
            boolean electricityAvailable,
            boolean waterAvailable,
            boolean drainageAvailable,
            boolean internetAvailable,
            BigDecimal price,
            OffsetDateTime now) {
        this.boothCode = boothCode;
        this.boothType = boothType;
        this.floorName = floorName;
        this.zoneName = zoneName;
        this.locationDescription = locationDescription;
        this.widthMeter = widthMeter;
        this.depthMeter = depthMeter;
        this.areaSqm = areaSqm;
        this.basicEquipment = basicEquipment;
        this.electricityAvailable = electricityAvailable;
        this.waterAvailable = waterAvailable;
        this.drainageAvailable = drainageAvailable;
        this.internetAvailable = internetAvailable;
        this.price = price;
        this.updatedAt = now;
    }

    public void changeStatus(BoothStatus status, OffsetDateTime now) {
        this.status = status;
        this.updatedAt = now;
    }

    /**
     * 배정된 부스를 AVAILABLE로 되돌린다. 배정 조직 기록과 함께 이전 조직이 등록한 소개
     * 정보(updateIntro로 설정한 필드들)도 지워야 한다 - 안 지우면 AVAILABLE 상태에서 공개
     * 목록(listPublic)에 이전 조직의 소개가 그대로 노출되고, 다른 조직이 재배정돼도
     * updateIntro를 다시 호출하기 전까지 이전 조직의 정보가 새 조직 부스로 남는다.
     */
    public void unassign(OffsetDateTime now) {
        this.status = BoothStatus.AVAILABLE;
        this.assignedOrganizationId = null;
        this.displayName = null;
        this.shortIntro = null;
        this.description = null;
        this.exhibitionContent = null;
        this.representativeFileId = null;
        this.updatedAt = now;
    }

    public void updateIntro(
            String displayName,
            String shortIntro,
            String description,
            String exhibitionContent,
            Long representativeFileId,
            OffsetDateTime now) {
        this.displayName = displayName;
        this.shortIntro = shortIntro;
        this.description = description;
        this.exhibitionContent = exhibitionContent;
        this.representativeFileId = representativeFileId;
        this.updatedAt = now;
    }

    public void issueQrToken(String qrToken, OffsetDateTime now) {
        this.qrToken = qrToken;
        this.qrIssuedAt = now;
        this.updatedAt = now;
    }

    /**
     * 부스 신청 접수 시 상태를 APPLICATION_PENDING으로 변경
     */
    public void markAsPending(OffsetDateTime now) {
        this.status = BoothStatus.APPLICATION_PENDING;
        this.updatedAt = now;
    }

    /**
     * 신청 취소/반려 시 부스 상태를 AVAILABLE로 복원
     */
    public void markAsAvailable(OffsetDateTime now) {
        this.status = BoothStatus.AVAILABLE;
        this.updatedAt = now;
    }

    /**
     * 신청 승인 시 부스를 ASSIGNED 상태로 변경하고 배정 조직 기록
     */
    public void markAsAssigned(Long organizationId, OffsetDateTime now) {
        this.status = BoothStatus.ASSIGNED;
        this.assignedOrganizationId = organizationId;
        this.updatedAt = now;
    }
}
