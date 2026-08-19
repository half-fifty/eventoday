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
     * 부스의 평균 평점 조회 (숨김 처리된 리뷰는 제외)
     * ✅ 반환타입: Optional<Double> (리뷰가 없을 수 있음)
     */
    @Query("SELECT AVG(br.rating) FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false")
    Optional<Double> findAverageRatingByBoothId(@Param("boothId") Long boothId);

    /**
     * 부스의 리뷰 개수 조회 (숨김 처리된 리뷰는 제외) — 메서드 시그니처는 유지하고
     * 파생 쿼리 대신 명시적 @Query로 바꿔 hidden 조건만 추가한다 (호출부 변경 불필요).
     */
    @Query("SELECT COUNT(br) FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false")
    long countByBoothId(@Param("boothId") Long boothId);

    /**
     * 부스별 리뷰 목록 (최신순, 숨김 처리된 리뷰는 공개 목록에서 제외)
     */
    @Query("SELECT br FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false ORDER BY br.createdAt DESC")
    Page<BoothReview> findByBoothIdOrderByCreatedAtDesc(@Param("boothId") Long boothId, Pageable pageable);

    /**
     * 부스별 리뷰 목록 (평점 높은 순 → 같은 평점이면 최신순)
     */
    @Query("SELECT br FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false "
            + "ORDER BY br.rating DESC, br.createdAt DESC")
    Page<BoothReview> findByBoothIdOrderByRatingDesc(@Param("boothId") Long boothId, Pageable pageable);

    /**
     * 부스별 리뷰 목록 (평점 낮은 순 → 같은 평점이면 최신순)
     */
    @Query("SELECT br FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false "
            + "ORDER BY br.rating ASC, br.createdAt DESC")
    Page<BoothReview> findByBoothIdOrderByRatingAsc(@Param("boothId") Long boothId, Pageable pageable);

    /**
     * 사용자의 모든 리뷰 목록 (최신순) — 본인 것이므로 숨김 여부와 무관하게 전부 보여준다.
     */
    Page<BoothReview> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    /**
     * 부스별 리뷰 검색 (키워드, 숨김 처리된 리뷰는 제외)
     */
    @Query("SELECT br FROM BoothReview br WHERE br.boothId = :boothId AND br.hidden = false "
            + "AND LOWER(br.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<BoothReview> findByBoothIdAndCommentContainingIgnoreCase(
            @Param("boothId") Long boothId, @Param("keyword") String keyword, Pageable pageable);

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
    @Query("SELECT br.boothId, AVG(br.rating) FROM BoothReview br WHERE br.boothId IN :boothIds AND br.hidden = false GROUP BY br.boothId")
    List<Object[]> findAverageRatingsByBoothIds(@Param("boothIds") Collection<Long> boothIds);

    /**
     * 여러 부스의 후기 개수를 한 번에 조회 (숨김 처리된 리뷰는 제외)
     *
     * @param boothIds 부스 ID 목록
     * @return [[boothId, count], ...] 형태
     */
    @Query("SELECT br.boothId, COUNT(br.id) FROM BoothReview br WHERE br.boothId IN :boothIds AND br.hidden = false GROUP BY br.boothId")
    List<Object[]> findReviewCountsByBoothIds(@Param("boothIds") Collection<Long> boothIds);

    // ===== 리뷰 AI 요약용 =====
    // hidden = false 조건이 여기 한 곳에만 있어도, 이 상수를 쓰는 세 쿼리(개수/목록/최신수정시각) 전부에
    // 자동으로 적용된다 — 숨김 처리된 리뷰는 AI 요약 재료에서도 함께 제외되는 게 핵심.
    String COMMENTED_REVIEW_CONDITION =
            "br.boothId = :boothId AND br.hidden = false AND br.comment IS NOT NULL AND TRIM(br.comment) <> ''";

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