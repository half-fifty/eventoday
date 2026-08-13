package com.min.edu.organization.domain;

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

/**
 * 개최자(ORGANIZER) 가입 신청 1건에 대한 심사 이력.
 * 반려 후 재신청 시 organization_id는 같고 새 행이 추가된다.
 */
@Entity
@Table(name = "organization_signup_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrganizationSignupReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationSignupReviewStatus status;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "business_registration_file_id")
    private Long businessRegistrationFileId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static OrganizationSignupReview create(
            Long organizationId,
            Long businessRegistrationFileId,
            OffsetDateTime now) {
        return OrganizationSignupReview.builder()
            .organizationId(organizationId)
            .status(OrganizationSignupReviewStatus.PENDING)
            .submittedAt(now)
            .businessRegistrationFileId(businessRegistrationFileId)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void approve(Long reviewerId, OffsetDateTime now) {
        requireStatus(OrganizationSignupReviewStatus.PENDING);
        this.status = OrganizationSignupReviewStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    private void requireStatus(OrganizationSignupReviewStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException("허용되지 않는 심사 상태 전환입니다.");
        }
    }
}
