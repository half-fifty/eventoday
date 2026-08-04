package com.min.edu.booth.repository;

import com.min.edu.booth.domain.Booth;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothRepository extends JpaRepository<Booth, Long> {
}