package com.min.edu.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember> findByOrganizationIdAndOrganizationRole(
        Long organizationId,
        OrganizationRole organizationRole
    );

    List<OrganizationMember> findByOrganizationIdInAndOrganizationRole(
        Collection<Long> organizationIds,
        OrganizationRole organizationRole
    );

    Optional<OrganizationMember> findFirstByMemberIdAndStatusOrderByIdAsc(
        Long memberId,
        OrganizationMemberStatus status
    );

    boolean existsByOrganizationIdAndMemberIdAndStatus(
        Long organizationId,
        Long memberId,
        OrganizationMemberStatus status
    );
}
