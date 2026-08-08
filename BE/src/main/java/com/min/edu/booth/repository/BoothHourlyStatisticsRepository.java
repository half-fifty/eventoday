package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothHourlyStatistics;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothHourlyStatisticsRepository extends JpaRepository<BoothHourlyStatistics, Long> {

    // 특정 부스 · 특정 날짜의 시간대별 통계 조회 (stat_hour 오름차순)
    List<BoothHourlyStatistics> findByBoothIdAndStatDateOrderByStatHourAsc(
            Long boothId, LocalDate statDate);
}