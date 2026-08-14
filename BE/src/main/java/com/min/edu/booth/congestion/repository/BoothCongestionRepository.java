package com.min.edu.booth.congestion.repository;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoothCongestionRepository extends JpaRepository<BoothCongestion, Long> {

    // 부스의 최신 혼잡도 조회
    @Query("SELECT bc FROM BoothCongestion bc " +
            "WHERE bc.booth.id = :boothId " +
            "ORDER BY bc.recordedAt DESC LIMIT 1")
    Optional<BoothCongestion> findLatestByBoothId(@Param("boothId") Long boothId);

    // 이벤트의 인기 부스 (혼잡도 높은 순서)
    @Query("SELECT bc FROM BoothCongestion bc " +
            "WHERE bc.booth.eventId = :eventId " +  // ← booth.event → booth.eventId
            "AND bc.id IN (" +
            "  SELECT MAX(bc2.id) FROM BoothCongestion bc2 " +
            "  WHERE bc2.booth.eventId = :eventId " +  // ← booth.event → booth.eventId
            "  GROUP BY bc2.booth.id" +
            ") " +
            "ORDER BY CASE WHEN bc.congestionLevel = 'HIGH' THEN 0 " +
            "            WHEN bc.congestionLevel = 'MEDIUM' THEN 1 " +
            "            ELSE 2 END, " +
            "        bc.estimatedWaitTime DESC")
    List<BoothCongestion> findPopularBoothsByEvent(@Param("eventId") Long eventId);

    // 이벤트의 한산한 부스 (혼잡도 낮은 순서)
    @Query("SELECT bc FROM BoothCongestion bc " +
            "WHERE bc.booth.eventId = :eventId " +  // ← booth.event → booth.eventId
            "AND bc.id IN (" +
            "  SELECT MAX(bc2.id) FROM BoothCongestion bc2 " +
            "  WHERE bc2.booth.eventId = :eventId " +  // ← booth.event → booth.eventId
            "  GROUP BY bc2.booth.id" +
            ") " +
            "ORDER BY CASE WHEN bc.congestionLevel = 'LOW' THEN 0 " +
            "            WHEN bc.congestionLevel = 'MEDIUM' THEN 1 " +
            "            ELSE 2 END, " +
            "        bc.estimatedWaitTime ASC")
    List<BoothCongestion> findUncrowdedBoothsByEvent(@Param("eventId") Long eventId);
}