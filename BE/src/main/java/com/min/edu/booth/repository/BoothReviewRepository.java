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

    // 기존 메서드들
    Optional<BoothReview> findByMemberIdAndBoothId(Long memberId, Long boothId);

    Optional<Double> findAverageRatingByBoothId(Long boothId);

    long countByBoothId(Long boothId);

    Page<BoothReview> findByBoothIdOrderByCreatedAtDesc(Long boothId, Pageable pageable);

    Page<BoothReview> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    Page<BoothReview> findByBoothIdAndCommentContainingIgnoreCase(Long boothId, String keyword, Pageable pageable);

    Optional<BoothReview> findByIdAndBoothId(Long id, Long boothId);

    // ===== 새로운 Batch 쿼리 메서드 =====

    /**
     * 여러 부스의 평점을 Batch로 조회
     *
     * @param boothIds 부스 ID 목록
     * @return [[boothId, averageRating], ...]
     */
    @Query("SELECT br.boothId, AVG(br.rating) FROM BoothReview br WHERE br.boothId IN :boothIds GROUP BY br.boothId")
    List<Object[]> findAverageRatingsByBoothIds(@Param("boothIds") Collection<Long> boothIds);

    /**
     * 여러 부스의 후기 수를 Batch로 조회
     *
     * @param boothIds 부스 ID 목록
     * @return [[boothId, count], ...]
     */
    @Query("SELECT br.boothId, COUNT(br.id) FROM BoothReview br WHERE br.boothId IN :boothIds GROUP BY br.boothId")
    List<Object[]> findReviewCountsByBoothIds(@Param("boothIds") Collection<Long> boothIds);
}