package com.min.edu.funnel.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 선택한 날짜 기준, 방문수가 많은 순으로 정렬한 행사별 퍼널 랭킹 (플랫폼 관리자 전용).
 */
public record FunnelEventRankingResponse(
        LocalDate reportDate,
        List<FunnelEventRankingItem> events) {
}
