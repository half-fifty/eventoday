package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothHourlyStatistics;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothHourlyStatisticsRepository extends JpaRepository<BoothHourlyStatistics, Long> {

    // 특정 부스 · 특정 날짜의 시간대별 통계 조회 (stat_hour 오름차순)
    List<BoothHourlyStatistics> findByBoothIdAndStatDateOrderByStatHourAsc(
            Long boothId, LocalDate statDate);

    /**
     * 기간 내 부스별 통계 합산 (예약수 내림차순)
     * STAT-API-003 인기 부스 순위, STAT-API-004 행사 요약에서 사용
     */
    @Query("SELECT bhs.boothId AS boothId, " +
           "SUM(bhs.reservationCount) AS totalReservationCount, " +
           "SUM(bhs.noShowCount) AS totalNoShowCount, " +
           "SUM(bhs.qrScanCount) AS totalQrScanCount " +
           "FROM BoothHourlyStatistics bhs " +
           "WHERE bhs.boothId IN :boothIds " +
           "AND bhs.statDate BETWEEN :from AND :to " +
           "GROUP BY bhs.boothId " +
           "ORDER BY SUM(bhs.reservationCount) DESC, bhs.boothId ASC")
    List<BoothStatAggregation> aggregateByBoothIdsAndDateBetween(
            @Param("boothIds") Collection<Long> boothIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}