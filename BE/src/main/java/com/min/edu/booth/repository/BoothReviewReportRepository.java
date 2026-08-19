package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothReviewReportRepository extends JpaRepository<BoothReviewReport, Long> {

    boolean existsByBoothReviewIdAndReporterMemberId(Long boothReviewId, Long reporterMemberId);

    long countByBoothReviewId(Long boothReviewId);
}
