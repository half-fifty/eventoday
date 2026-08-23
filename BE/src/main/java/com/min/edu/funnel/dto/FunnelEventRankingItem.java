package com.min.edu.funnel.dto;

public record FunnelEventRankingItem(
        Long eventId,
        String eventName,
        long totalSessions,
        long completePaymentCount) {
}
