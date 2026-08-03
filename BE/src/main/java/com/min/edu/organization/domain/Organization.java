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
            OffsetDateTime now) {
        return Organization.builder()
            .organizationType(organizationType)
            .name(name)
            .businessNumber(businessNumber)
            .representativeName(representativeName)
            .contactEmail(contactEmail)
            .contactPhone(contactPhone)
            .status(OrganizationStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
