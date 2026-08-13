package com.min.edu.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.organization.domain.BusinessMemberProfile;

public interface BusinessMemberProfileRepository
        extends JpaRepository<BusinessMemberProfile, Long> {
}
