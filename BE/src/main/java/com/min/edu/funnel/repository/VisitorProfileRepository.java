package com.min.edu.funnel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.funnel.domain.VisitorProfile;

public interface VisitorProfileRepository extends JpaRepository<VisitorProfile, Long> {

    Optional<VisitorProfile> findByVisitorKey(String visitorKey);
}
