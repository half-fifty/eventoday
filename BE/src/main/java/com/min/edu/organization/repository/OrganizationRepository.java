package com.min.edu.organization.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.organization.domain.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByBusinessNumber(String businessNumber);

    Optional<Organization> findByBusinessNumber(String businessNumber);
}
