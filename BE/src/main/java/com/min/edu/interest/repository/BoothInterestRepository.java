package com.min.edu.interest.repository;

import com.min.edu.booth.domain.BoothInterest;
import com.min.edu.interest.dto.InterestBoothResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoothInterestRepository extends JpaRepository<BoothInterest, Long> {

    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);

    Optional<BoothInterest> findByMemberIdAndBoothId(Long memberId, Long boothId);

    List<BoothInterest> findAllByMemberId(Long memberId);

    @Modifying
    @Query(value = """
            INSERT INTO booth_interests (member_id, booth_id, vacancy_notification_enabled, created_at)
            VALUES (:memberId, :boothId, false, now())
            ON CONFLICT (member_id, booth_id) DO NOTHING
            """, nativeQuery = true)
    void upsertInterest(@Param("memberId") Long memberId, @Param("boothId") Long boothId);

    @Query("""
            select new com.min.edu.interest.dto.InterestBoothResponse(
                bi.boothId,
                b.displayName,
                b.shortIntro,
                bi.vacancyNotificationEnabled)
            from BoothInterest bi
            join Booth b on b.id = bi.boothId
            where bi.memberId = :memberId
            """)
    List<InterestBoothResponse> findInterestBoothsByMemberId(@Param("memberId") Long memberId);
}
