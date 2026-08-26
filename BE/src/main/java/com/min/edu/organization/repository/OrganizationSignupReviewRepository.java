package com.min.edu.organization.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.organization.domain.OrganizationSignupReview;
import com.min.edu.organization.domain.OrganizationSignupReviewStatus;

import jakarta.persistence.LockModeType;

public interface OrganizationSignupReviewRepository
        extends JpaRepository<OrganizationSignupReview, Long> {

    Page<OrganizationSignupReview> findByStatus(
        OrganizationSignupReviewStatus status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrganizationSignupReview r where r.id = :id")
    Optional<OrganizationSignupReview> findByIdForUpdate(@Param("id") Long id);

    void deleteAllByOrganizationId(Long organizationId);
}
