package com.min.edu.recruitment.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 행사 ID로 모집공고 ID 목록 조회 (신청 목록 검색용)
    @Query("SELECT r.id FROM BoothRecruitment r WHERE r.eventId = :eventId")
    List<Long> findIdsByEventId(@Param("eventId") Long eventId);
}
