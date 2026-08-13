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

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 20)
    private OrganizationType organizationType;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "business_number", unique = true, length = 20)
    private String businessNumber;

    @Column(name = "representative_name", length = 50)
    private String representativeName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address_line1", length = 300)
    private String addressLine1;

    @Column(name = "address_line2", length = 300)
    private String addressLine2;

    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    @Column(name = "introduction", length = 1000)
    private String introduction;

    @Column(name = "logo_file_id")
    private Long logoFileId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Organization createBusinessOrganization(
            OrganizationType organizationType,
            String name,
            String businessNumber,
            String representativeName,
            String contactEmail,
            String contactPhone,
            String postalCode,
            String addressLine1,
            String addressLine2,
            String homepageUrl,
            String introduction,
            Long logoFileId,
            OffsetDateTime now) {
        OrganizationStatus initialStatus = organizationType == OrganizationType.EXHIBITOR
            ? OrganizationStatus.ACTIVE
            : OrganizationStatus.PENDING;

        return Organization.builder()
            .organizationType(organizationType)
            .name(name)
            .businessNumber(businessNumber)
            .representativeName(representativeName)
            .contactEmail(contactEmail)
            .contactPhone(contactPhone)
            .status(initialStatus)
            .postalCode(postalCode)
            .addressLine1(addressLine1)
            .addressLine2(addressLine2)
            .homepageUrl(homepageUrl)
            .introduction(introduction)
            .logoFileId(logoFileId)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void updateBusinessInfo(
            String name,
            String representativeName,
            String contactEmail,
            String contactPhone,
            String postalCode,
            String addressLine1,
            String addressLine2,
            String homepageUrl,
            String introduction,
            Long logoFileId,
            OffsetDateTime now) {
        this.name = name;
        this.representativeName = representativeName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.postalCode = postalCode;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.homepageUrl = homepageUrl;
        this.introduction = introduction;
        this.logoFileId = logoFileId;
        this.updatedAt = now;
    }

    public void approve(OffsetDateTime now) {
        requireStatus(OrganizationStatus.PENDING);
        this.status = OrganizationStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void suspend(OffsetDateTime now) {
        requireStatus(OrganizationStatus.ACTIVE);
        this.status = OrganizationStatus.SUSPENDED;
        this.updatedAt = now;
    }

    private void requireStatus(OrganizationStatus... allowed) {
        for (OrganizationStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("허용되지 않는 조직 상태 전환입니다.");
    }
}
