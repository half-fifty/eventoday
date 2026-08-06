package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothApplication;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothApplicationRepository extends JpaRepository<BoothApplication, Long>,
        JpaSpecificationExecutor<BoothApplication> {

    boolean existsByApplicationNo(String applicationNo);

    // 조직 ID로 해당 조직의 신청 목록 조회 (submittedAt 최신순)
    List<BoothApplication> findAllByApplicantOrganizationIdOrderBySubmittedAtDesc(Long applicantOrganizationId);

    // 취소 처리 시 동시 요청 방어를 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BoothApplication a WHERE a.id = :id")
    Optional<BoothApplication> findByIdWithLock(@Param("id") Long id);
}