package com.min.edu.interest.repository;

import com.min.edu.booth.domain.BoothInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BoothInterestRepository extends JpaRepository<BoothInterest, Long> {

    // ===== 기존 메서드 =====

    /**
     * 사용자의 특정 부스 관심도 존재 확인
     */
    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);

    /**
     * 특정 부스의 빈자리 알림이 활성화된 관심도 목록 조회
     */
    List<BoothInterest> findByBoothIdAndVacancyNotificationEnabledTrue(Long boothId);

    // ===== 새로운 메서드 =====

    /**
     * 1. 사용자의 모든 관심 부스 기본 정보 조회 (Booth 정보 포함)
     *
     * 평점/후기수/예약가능여부/혼잡도는 부스마다 별도 소스(배치 쿼리 + 실시간 집계)에서
     * 가져와야 해서 여기서는 기본 필드만 조회하고, 나머지는 서비스 레이어에서 조립한다.
     * (row = [boothId, eventId, displayName, shortIntro, vacancyNotificationEnabled])
     *
     * @param memberId 회원 ID
     * @return 사용자가 관심 표시한 부스 기본 정보 목록
     */
    @Query("SELECT bi.boothId, b.eventId, b.displayName, b.shortIntro, bi.vacancyNotificationEnabled " +
            "FROM BoothInterest bi " +
            "JOIN Booth b ON bi.boothId = b.id " +
            "WHERE bi.memberId = :memberId")
    List<Object[]> findInterestBoothsByMemberId(@Param("memberId") Long memberId);

    /**
     * 2. 사용자가 관심 표시한 모든 부스 ID를 Batch로 조회
     *
     * @param memberId 회원 ID
     * @return 관심 표시한 부스 ID 목록
     */
    @Query("SELECT bi.boothId FROM BoothInterest bi WHERE bi.memberId = :memberId")
    Set<Long> findBoothIdsByMemberId(@Param("memberId") Long memberId);

    /**
     * 3. 사용자의 특정 부스 관심도 조회
     */
    Optional<BoothInterest> findByMemberIdAndBoothId(Long memberId, Long boothId);

    /**
     * 4. 사용자의 모든 관심 부스 조회 (엔티티 반환)
     */
    List<BoothInterest> findByMemberId(Long memberId);
}