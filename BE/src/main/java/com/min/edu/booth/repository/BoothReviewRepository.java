package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoothReviewRepository extends JpaRepository<BoothReview, Long> {

    /**
     * 사용자가 특정 부스에 작성한 리뷰 조회 (중복 확인용)
     */
    Optional<BoothReview> findByMemberIdAndBoothId(Long memberId, Long boothId);

    /**
     * 부스의 평균 평점 조회
     * ✅ 반환타입: Optional<Double> (리뷰가 없을 수 있음)
     */
    @Query("SELECT AVG(br.rating) FROM BoothReview br WHERE br.boothId = :boothId")
    Optional<Double> findAverageRatingByBoothId(@Param("boothId") Long boothId);

    /**
     * 부스의 리뷰 개수 조회
     */
    long countByBoothId(Long boothId);

    /**
     * 부스별 리뷰 목록 (최신순)
     */
    Page<BoothReview> findByBoothIdOrderByCreatedAtDesc(Long boothId, Pageable pageable);

    /**
     * 사용자의 모든 리뷰 목록 (최신순)
     */
    Page<BoothReview> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    /**
     * 부스별 리뷰 검색 (키워드)
     */
    Page<BoothReview> findByBoothIdAndCommentContainingIgnoreCase(Long boothId, String keyword, Pageable pageable);

    /**
     * 특정 부스의 특정 리뷰 조회
     */
    Optional<BoothReview> findByIdAndBoothId(Long id, Long boothId);


    // ===== Batch 쿼리 메서드 (N+1 최적화) =====

    /**
     * 여러 부스의 평점을 한 번에 조회
     *
     * @param boothIds 부스 ID 목록
     * @return [[boothId, averageRating], ...] 형태
     */
    @Query("SELECT br.boothId, AVG(br.rating) FROM BoothReview br WHERE br.boothId IN :boothIds GROUP BY br.boothId")
    List<Object[]> findAverageRatingsByBoothIds(@Param("boothIds") Collection<Long> boothIds);

    /**
     * 여러 부스의 후기 개수를 한 번에 조회
     *
     * @param boothIds 부스 ID 목록
     * @return [[boothId, count], ...] 형태
     */
    @Query("SELECT br.boothId, COUNT(br.id) FROM BoothReview br WHERE br.boothId IN :boothIds GROUP BY br.boothId")
    List<Object[]> findReviewCountsByBoothIds(@Param("boothIds") Collection<Long> boothIds);

    // ===== 리뷰 AI 요약용 =====

    String COMMENTED_REVIEW_CONDITION = "br.boothId = :boothId AND br.comment IS NOT NULL AND TRIM(br.comment) <> ''";

    /**
     * 코멘트가 실제로 채워진(공백 제외) 리뷰 개수 — 요약 생성/재생성 여부 판단 기준
     */
    @Query("SELECT COUNT(br.id) FROM BoothReview br WHERE " + COMMENTED_REVIEW_CONDITION)
    long countByBoothIdAndCommentIsNotBlank(@Param("boothId") Long boothId);

    /**
     * 코멘트가 채워진 최신 리뷰 목록 (요약 프롬프트 입력용, Pageable로 개수 제한)
     */
    @Query("SELECT br FROM BoothReview br WHERE " + COMMENTED_REVIEW_CONDITION + " ORDER BY br.createdAt DESC")
    List<BoothReview> findRecentCommentedReviews(@Param("boothId") Long boothId, Pageable pageable);

    /**
     * 코멘트가 채워진 리뷰의 최신 수정 시각 — 리뷰 수정/삭제+추가처럼 개수가 유지되는 변경까지 요약 캐시 무효화 기준에 반영하기 위함
     */
    @Query("SELECT MAX(br.updatedAt) FROM BoothReview br WHERE " + COMMENTED_REVIEW_CONDITION)
    Optional<OffsetDateTime> findMaxUpdatedAtByBoothIdAndCommentIsNotBlank(@Param("boothId") Long boothId);
}