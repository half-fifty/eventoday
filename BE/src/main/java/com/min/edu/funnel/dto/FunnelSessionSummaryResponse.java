package com.min.edu.funnel.dto;

import java.time.LocalDate;

/**
 * FunnelSession 원본을 그 자리에서 집계한 결과. 이상탐지/AI 코멘트는 아직 없다
 * (P0.5/P1에서 FunnelDiagnosisReport로 대체될 예정 — technical-design.md 참고).
 */
public record FunnelSessionSummaryResponse(
        Long eventId,
        LocalDate reportDate,
        long totalSessions,
        long viewEventDetailCount,
        long openPurchaseModalCount,
        long completePaymentCount,
        long droppedCount,
        long boothExploredCount,
        long returningVisitorCount) {
}
