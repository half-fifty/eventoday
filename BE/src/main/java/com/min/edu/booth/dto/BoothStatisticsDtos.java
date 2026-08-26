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

    // STAT-API-003 인기 부스 단일 항목
    public record PopularBoothEntry(
            int rank,
            Long boothId,
            String boothCode,
            long totalReservationCount,
            long totalNoShowCount,
            long totalQrScanCount
    ) {}

    // STAT-API-003 응답
    public record PopularBoothsSummary(
            Long eventId,
            LocalDate from,
            LocalDate to,
            List<PopularBoothEntry> booths
    ) {}

    // STAT-API-004 부스별 요약 항목
    public record BoothStatSummary(
            Long boothId,
            String boothCode,
            long totalReservationCount,
            long totalNoShowCount,
            long totalQrScanCount
    ) {}

    // STAT-API-004 응답 (행사 운영 통계 요약)
    public record EventOverviewSummary(
            Long eventId,
            LocalDate from,
            LocalDate to,
            long totalReservationCount,
            long totalNoShowCount,
            long totalQrScanCount,
            List<BoothStatSummary> boothSummaries
    ) {}
}