package com.min.edu.event.repository;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventBoothRecruitmentRepository extends JpaRepository<BoothRecruitment, Long> {
    boolean existsByEventIdAndStatus(Long eventId, BoothRecruitmentStatus status);
}
