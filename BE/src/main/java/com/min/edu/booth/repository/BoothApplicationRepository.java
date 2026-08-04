package com.min.edu.booth.repository;

import java.util.List;
import com.min.edu.booth.domain.BoothApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothApplicationRepository extends JpaRepository<BoothApplication, Long> {
    boolean existsByApplicationNo(String applicationNo);

    // 조직 ID로 해당 조직의 신청 목록 조회 (submittedAt 최신순)
    List<BoothApplication> findAllByApplicantOrganizationIdOrderBySubmittedAtDesc(Long applicantOrganizationId);

}