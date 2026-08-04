package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothApplicationRepository extends JpaRepository<BoothApplication, Long> {
    boolean existsByApplicationNo(String applicationNo);
}