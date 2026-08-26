package com.min.edu.event.repository;

import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    boolean existsByOrganizationIdAndMemberIdAndStatus(
            Long organizationId, Long memberId, OrganizationMemberStatus status);
    boolean existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            Long organizationId, Long memberId, OrganizationMemberStatus status,
            Collection<OrganizationRole> roles);
    List<OrganizationMember> findAllByMemberIdAndStatusAndOrganizationRoleIn(
            Long memberId, OrganizationMemberStatus status, Collection<OrganizationRole> roles);
    /** 역할과 무관하게 회원이 속한 조직 (공지 공개 대상 판정용 - CONTENT-003) */
    List<OrganizationMember> findAllByMemberIdAndStatus(
            Long memberId, OrganizationMemberStatus status);
    List<OrganizationMember> findAllByOrganizationIdAndStatusAndOrganizationRoleIn(
            Long organizationId, OrganizationMemberStatus status, Collection<OrganizationRole> roles);
}
