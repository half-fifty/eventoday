package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewSummaryBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothReviewSummaryBatchRepository extends JpaRepository<BoothReviewSummaryBatch, Long> {

    List<BoothReviewSummaryBatch> findByBoothIdOrderByBatchIndexAsc(Long boothId);
}
