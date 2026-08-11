package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}