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

    /**
     * 신청서 생성 팩토리 메서드 - BoothRecruitment.create() 패턴과 동일
     */
    public static BoothApplication create(
            String applicationNo,
            Long recruitmentId,
            Long boothId,
            Long applicantOrganizationId,
            Long applicantMemberId,
            String teamName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String activityDescription,
            String exhibitionContent,
            Integer expectedVisitors,
            boolean electricityRequired,
            boolean waterRequired,
            boolean drainageRequired,
            boolean internetRequired,
            String applicationReason,
            OffsetDateTime now) {
        return BoothApplication.builder()
                .applicationNo(applicationNo)
                .recruitmentId(recruitmentId)
                .boothId(boothId)
                .applicantOrganizationId(applicantOrganizationId)
                .applicantMemberId(applicantMemberId)
                .teamName(teamName)
                .contactName(contactName)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .activityDescription(activityDescription)
                .exhibitionContent(exhibitionContent)
                .expectedVisitors(expectedVisitors)
                .electricityRequired(electricityRequired)
                .waterRequired(waterRequired)
                .drainageRequired(drainageRequired)
                .internetRequired(internetRequired)
                .applicationReason(applicationReason)
                .status(BoothApplicationStatus.SUBMITTED)
                .submittedAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 신청 취소 - cancelledAt 기록, 상태 CANCELLED로 변경
     */
    public void cancel(OffsetDateTime now) {
        this.status = BoothApplicationStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    /**
     * 검토 시작 - 상태 UNDER_REVIEW로 변경, 검토 담당자 기록
     */
    public void startReview(Long reviewerMemberId, OffsetDateTime now) {
        this.status = BoothApplicationStatus.UNDER_REVIEW;
        this.reviewedBy = reviewerMemberId;
        this.updatedAt = now;
    }

    /**
     * 신청 승인 - 상태 APPROVED로 변경, 검토 담당자·검토 시각 기록
     * reviewedAt은 최종 처리(승인/반려) 시각
     */
    public void approve(Long reviewerMemberId, OffsetDateTime now) {
        this.status = BoothApplicationStatus.APPROVED;
        this.reviewedBy = reviewerMemberId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }
}
