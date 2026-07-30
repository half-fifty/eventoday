package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booth_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "application_no", nullable = false, unique = true, length = 40)
    private String applicationNo;

    @Column(name = "recruitment_id", nullable = false)
    private Long recruitmentId;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "applicant_organization_id", nullable = false)
    private Long applicantOrganizationId;

    @Column(name = "applicant_member_id", nullable = false)
    private Long applicantMemberId;

    @Column(name = "team_name", nullable = false, length = 150)
    private String teamName;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "activity_description", nullable = false, columnDefinition = "TEXT")
    private String activityDescription;

    @Column(name = "exhibition_content", nullable = false, columnDefinition = "TEXT")
    private String exhibitionContent;

    @Column(name = "expected_visitors")
    private Integer expectedVisitors;

    @Column(name = "electricity_required", nullable = false)
    private boolean electricityRequired;

    @Column(name = "water_required", nullable = false)
    private boolean waterRequired;

    @Column(name = "drainage_required", nullable = false)
    private boolean drainageRequired;

    @Column(name = "internet_required", nullable = false)
    private boolean internetRequired;

    @Column(name = "application_reason", columnDefinition = "TEXT")
    private String applicationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BoothApplicationStatus status;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
