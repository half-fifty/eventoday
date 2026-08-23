package com.min.edu.event.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record VenueParkingStatusDto(
        String venueId,
        String status,
        String statusLabel,
        Integer expectedFullHour,
        List<Integer> hourlyCounts,
        Thresholds thresholds,
        OffsetDateTime fetchedAt,
        boolean stale,
        String notice,
        String sourceUrl) {

    public record Thresholds(int smooth, int congested, int full) {}

    public VenueParkingStatusDto asStale(String staleNotice) {
        return new VenueParkingStatusDto(
                venueId, status, statusLabel, expectedFullHour, hourlyCounts, thresholds,
                fetchedAt, true, staleNotice, sourceUrl);
    }
}
