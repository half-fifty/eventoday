package com.min.edu.organization.dto;

import java.time.OffsetDateTime;

import com.min.edu.organization.domain.OrganizationSignupReviewStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrganizationSignupReviewSummaryDto {

    private Long reviewId;
    private Long organizationId;
    private String organizationName;
    private String representativeName;
    private String maskedBusinessNumber;
    private String managerName;
    private OffsetDateTime submittedAt;
    private OrganizationSignupReviewStatus status;
}
