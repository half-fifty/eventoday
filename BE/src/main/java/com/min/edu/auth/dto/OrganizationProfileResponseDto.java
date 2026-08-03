package com.min.edu.auth.dto;

import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.organization.domain.OrganizationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationProfileResponseDto {

    private Long organizationId;
    private String name;
    private OrganizationType organizationType;
    private OrganizationRole organizationRole;

    public static OrganizationProfileResponseDto from(
            Organization organization,
            OrganizationMember organizationMember) {
        return OrganizationProfileResponseDto.builder()
            .organizationId(organization.getId())
            .name(organization.getName())
            .organizationType(organization.getOrganizationType())
            .organizationRole(organizationMember.getOrganizationRole())
            .build();
    }
}
