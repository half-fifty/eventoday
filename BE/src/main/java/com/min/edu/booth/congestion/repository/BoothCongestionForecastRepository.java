package com.min.edu.booth.congestion.repository;

import com.min.edu.booth.congestion.domain.BoothCongestionForecast;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoothCongestionForecastRepository extends JpaRepository<BoothCongestionForecast, Long> {

    // 특정 부스의 특정 날짜 시간대별 예측 조회
    @Query("SELECT bcf FROM BoothCongestionForecast bcf " +
            "WHERE bcf.booth.id = :boothId " +
            "AND bcf.forecastDate = :forecastDate " +
            "ORDER BY bcf.forecastHour ASC")
    List<BoothCongestionForecast> findByBoothIdAndForecastDate(
            @Param("boothId") Long boothId,
            @Param("forecastDate") LocalDate forecastDate
    );

    // 한산한 시간대 조회 (해당 날짜 LOW 혼잡도인 시간)
    @Query("SELECT bcf FROM BoothCongestionForecast bcf " +
            "WHERE bcf.booth.id = :boothId " +
            "AND bcf.forecastDate = :forecastDate " +
            "AND bcf.predictedCongestionLevel = 'LOW' " +
            "ORDER BY bcf.forecastHour ASC")
    List<BoothCongestionForecast> findBestTimeSlots(
            @Param("boothId") Long boothId,
            @Param("forecastDate") LocalDate forecastDate
    );

    // 최적 시간대 1개 (가장 빨리 오는 LOW 시간)
    @Query("SELECT bcf FROM BoothCongestionForecast bcf " +
            "WHERE bcf.booth.id = :boothId " +
            "AND bcf.forecastDate = :forecastDate " +
            "AND bcf.predictedCongestionLevel = 'LOW' " +
            "ORDER BY bcf.forecastHour ASC LIMIT 1")
    Optional<BoothCongestionForecast> findBestTimeSlot(
            @Param("boothId") Long boothId,
            @Param("forecastDate") LocalDate forecastDate
    );
}