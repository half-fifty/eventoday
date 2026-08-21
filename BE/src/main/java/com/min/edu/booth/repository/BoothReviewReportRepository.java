package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewReport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothReviewReportRepository extends JpaRepository<BoothReviewReport, Long> {

    boolean existsByBoothReviewIdAndReporterMemberId(Long boothReviewId, Long reporterMemberId);

    long countByBoothReviewId(Long boothReviewId);

    /**
     * 주어진 리뷰 ID들 중, 이 회원이 이미 신고한 리뷰의 ID만 골라낸다 (목록 조회 시 "내가 신고했는지" 배지 표시용).
     */
    List<Long> findBoothReviewIdByReporterMemberIdAndBoothReviewIdIn(
            Long reporterMemberId, Collection<Long> boothReviewIds);

    /**
     * 특정 부스에 속한 리뷰들에 달린 신고 전부를 리뷰별로 묶어 조회하기 위한 원본 목록
     * (운영자용 "신고된 리뷰" 대시보드).
     */
    @Query("SELECT r FROM BoothReviewReport r WHERE r.boothReviewId IN "
            + "(SELECT br.id FROM BoothReview br WHERE br.boothId = :boothId) "
            + "ORDER BY r.boothReviewId, r.createdAt DESC")
    List<BoothReviewReport> findByBoothId(@Param("boothId") Long boothId);
}
