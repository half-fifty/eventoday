package com.min.edu.organization.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.organization.domain.OrganizationSignupReview;
import com.min.edu.organization.domain.OrganizationSignupReviewStatus;

public interface OrganizationSignupReviewRepository
        extends JpaRepository<OrganizationSignupReview, Long> {

    Page<OrganizationSignupReview> findByStatus(
        OrganizationSignupReviewStatus status,
        Pageable pageable
    );

    void deleteAllByOrganizationId(Long organizationId);
}
