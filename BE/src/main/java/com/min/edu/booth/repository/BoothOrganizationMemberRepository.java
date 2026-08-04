package com.min.edu.booth.repository;

import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothOrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    boolean existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            Long organizationId, Long memberId, OrganizationMemberStatus status,
            Collection<OrganizationRole> roles);
}