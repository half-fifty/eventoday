package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 부스 예약/슬롯 관리 API 전반에서 쓰는 "이 부스의 운영자(OWNER/MANAGER)인가" 판단 로직을 한 곳에 모은다.
@Component
@RequiredArgsConstructor
public class BoothManagerPermissionChecker {

    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final BoothRepository boothRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;

    public void requireBoothManager(Long boothId, AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        Long organizationId = booth.getAssignedOrganizationId();
        if (organizationId == null
                || !boothOrganizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
