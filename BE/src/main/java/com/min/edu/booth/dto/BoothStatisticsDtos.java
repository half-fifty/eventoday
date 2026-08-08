package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothHourlyStatistics;
import java.time.LocalDate;
import java.util.List;

public final class BoothStatisticsDtos {

    private BoothStatisticsDtos() {}

    // 시간대별 단일 항목 (0~23시)
    public record HourlyEntry(
            int statHour,
            int reservationCount,
            int noShowCount,
            int qrScanCount
    ) {
        public static HourlyEntry from(BoothHourlyStatistics stat) {
            return new HourlyEntry(
                    stat.getStatHour(),
                    stat.getReservationCount(),
                    stat.getNoShowCount(),
                    stat.getQrScanCount()
            );
        }
    }

    // STAT-API-001 응답
    public record HourlySummary(
            Long boothId,
            LocalDate statDate,
            List<HourlyEntry> hourlyStats
    ) {}

    // STAT-API-002 응답 (전날 합산 + 시간대별 상세)
    public record PreviousDaySummary(
            Long boothId,
            LocalDate statDate,
            int totalReservationCount,
            int totalNoShowCount,
            int totalQrScanCount,
            List<HourlyEntry> hourlyStats
    ) {}
}