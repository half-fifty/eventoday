package com.min.edu.organization.dto;

import java.time.OffsetDateTime;

import com.min.edu.organization.domain.OrganizationSignupReviewStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrganizationSignupReviewDetailDto {

    private Long reviewId;
    private Long organizationId;
    private String organizationName;
    private String representativeName;
    private String maskedBusinessNumber;
    private String contactEmail;
    private String contactPhone;
    private String loginEmail;
    private String managerName;
    private String managerPhone;
    private String postalCode;
    private String addressLine1;
    private String addressLine2;
    private String homepageUrl;
    private String introduction;
    private Long businessRegistrationFileId;
    private String businessRegistrationFileDownloadUrl;
    private OffsetDateTime submittedAt;
    private OrganizationSignupReviewStatus status;
    private Long reviewedBy;
    private OffsetDateTime reviewedAt;
    private String rejectionReason;
}
