package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothReviewReportRepository extends JpaRepository<BoothReviewReport, Long> {

    boolean existsByBoothReviewIdAndReporterMemberId(Long boothReviewId, Long reporterMemberId);

    long countByBoothReviewId(Long boothReviewId);

    /**
     * 신고 취소용 — 본인이 넣은 신고 건을 찾아 삭제한다.
     */
    Optional<BoothReviewReport> findByBoothReviewIdAndReporterMemberId(
            Long boothReviewId, Long reporterMemberId);

    /**
     * 주어진 리뷰 ID들 중, 이 회원이 이미 신고한 리뷰의 ID만 골라낸다 (목록 조회 시 "내가 신고했는지" 배지 표시용).
     *
     * 파생 쿼리 이름(findBoothReviewIdBy...)만으로는 Spring Data JPA가 boothReviewId 컬럼만
     * 프로젝션하지 못하고 엔티티 전체를 반환해버려 List<Long> 변환 시 ConverterNotFoundException이
     * 나는 걸 로컬 테스트로 확인함 — 명시적 @Query로 컬럼을 직접 선택하도록 수정.
     */
    @Query("SELECT r.boothReviewId FROM BoothReviewReport r "
            + "WHERE r.reporterMemberId = :reporterMemberId AND r.boothReviewId IN :boothReviewIds")
    List<Long> findBoothReviewIdByReporterMemberIdAndBoothReviewIdIn(
            @Param("reporterMemberId") Long reporterMemberId,
            @Param("boothReviewIds") Collection<Long> boothReviewIds);

    /**
     * 특정 부스에 속한 리뷰들에 달린 신고 전부를 리뷰별로 묶어 조회하기 위한 원본 목록
     * (운영자용 "신고된 리뷰" 대시보드).
     */
    @Query("SELECT r FROM BoothReviewReport r WHERE r.boothReviewId IN "
            + "(SELECT br.id FROM BoothReview br WHERE br.boothId = :boothId) "
            + "ORDER BY r.boothReviewId, r.createdAt DESC")
    List<BoothReviewReport> findByBoothId(@Param("boothId") Long boothId);
}
