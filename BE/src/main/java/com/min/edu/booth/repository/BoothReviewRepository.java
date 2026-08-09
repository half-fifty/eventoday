package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoothReviewRepository extends JpaRepository<BoothReview, Long> {

    @Query("SELECT br FROM BoothReview br WHERE br.id = :id AND br.boothId = :boothId")
    Optional<BoothReview> findByIdAndBoothId(@Param("id") Long id, @Param("boothId") Long boothId);


    // 회원의 부스 리뷰 조회 (중복 방지)
    Optional<BoothReview> findByMemberIdAndBoothId(Long memberId, Long boothId);

    // 부스별 평균 별점 조회
    @Query("SELECT AVG(br.rating) FROM BoothReview br WHERE br.boothId = :boothId")
    Optional<Double> findAverageRatingByBoothId(@Param("boothId") Long boothId);

    // 부스별 리뷰 개수
    @Query("SELECT COUNT(br) FROM BoothReview br WHERE br.boothId = :boothId")
    long countByBoothId(@Param("boothId") Long boothId);

    // ===== WBS-159: 부스별 후기 목록 =====
    Page<BoothReview> findByBoothIdOrderByCreatedAtDesc(Long boothId, Pageable pageable);

    // ===== WBS-160: 내 작성 후기 목록 =====
    Page<BoothReview> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);


    Page<BoothReview> findByBoothIdAndCommentContainingIgnoreCase(
            Long boothId, String keyword, Pageable pageable);

}