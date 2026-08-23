package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewHelpfulVote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothReviewHelpfulVoteRepository extends JpaRepository<BoothReviewHelpfulVote, Long> {

    /**
     * 취소용 — 본인이 누른 "도움이 돼요"를 찾아 삭제한다.
     */
    Optional<BoothReviewHelpfulVote> findByBoothReviewIdAndMemberId(Long boothReviewId, Long memberId);

    long countByBoothReviewId(Long boothReviewId);

    /**
     * 주어진 리뷰 ID들 중, 이 회원이 이미 "도움이 돼요"를 누른 리뷰의 ID만 골라낸다 (목록 조회 시 배지 표시용).
     *
     * BoothReviewReportRepository와 동일한 이유로 명시적 @Query를 쓴다 — 파생 쿼리 이름만으로는
     * boothReviewId 컬럼만 프로젝션하지 못하고 엔티티 전체를 반환해 List<Long> 변환 시
     * ConverterNotFoundException이 난다.
     */
    @Query("SELECT v.boothReviewId FROM BoothReviewHelpfulVote v "
            + "WHERE v.memberId = :memberId AND v.boothReviewId IN :boothReviewIds")
    List<Long> findBoothReviewIdByMemberIdAndBoothReviewIdIn(
            @Param("memberId") Long memberId,
            @Param("boothReviewIds") Collection<Long> boothReviewIds);

    /**
     * 여러 리뷰의 "도움이 돼요" 개수를 한 번에 조회 (N+1 방지)
     *
     * @return [[boothReviewId, count], ...] 형태
     */
    @Query("SELECT v.boothReviewId, COUNT(v) FROM BoothReviewHelpfulVote v "
            + "WHERE v.boothReviewId IN :boothReviewIds GROUP BY v.boothReviewId")
    List<Object[]> countsByBoothReviewIdIn(@Param("boothReviewIds") Collection<Long> boothReviewIds);
}
