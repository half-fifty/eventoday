package com.min.edu.organization.dto;

import com.min.edu.organization.domain.OrganizationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BusinessSignupResponseDto {

    private Long memberId;
    private Long organizationId;
    private OrganizationStatus organizationStatus;
}
