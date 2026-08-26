package com.min.edu.funnel.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka funnel-actions 토픽으로 발행되는 메시지. FE 발행 액션과 결제 도메인이 발행하는
 * complete_payment 모두 이 스키마를 공유한다 (event-contract.md 참고).
 */
public record FunnelActionEventDto(
        String actionId,
        String sessionId,
        Long eventId,
        String anonymousId,
        Long userId,
        String actionType,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        Map<String, Object> properties) {

    public static FunnelActionEventDto create(
            String sessionId,
            Long eventId,
            String anonymousId,
            Long userId,
            String actionType,
            OffsetDateTime occurredAt,
            Map<String, Object> properties) {
        return new FunnelActionEventDto(
                UUID.randomUUID().toString(),
                sessionId,
                eventId,
                anonymousId,
                userId,
                actionType,
                occurredAt,
                OffsetDateTime.now(),
                properties == null ? Map.of() : properties);
    }
}
