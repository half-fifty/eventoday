package com.min.edu.recruitment.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;

public interface BoothRecruitmentRepository extends JpaRepository<BoothRecruitment, Long> {
    Optional<BoothRecruitment> findByEventId(Long eventId);

    boolean existsByEventId(Long eventId);

    List<BoothRecruitment> findAllByStatus(BoothRecruitmentStatus status);

    List<BoothRecruitment> findAllByStatusNot(BoothRecruitmentStatus status);

    List<BoothRecruitment> findAllByStatusAndRecruitmentStartAtLessThanEqual(
        BoothRecruitmentStatus status,
        OffsetDateTime recruitmentStartAt
    );

    List<BoothRecruitment> findAllByStatusAndRecruitmentEndAtLessThanEqual(
        BoothRecruitmentStatus status,
        OffsetDateTime recruitmentEndAt
    );
}
