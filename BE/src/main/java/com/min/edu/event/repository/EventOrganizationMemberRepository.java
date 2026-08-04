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
}
