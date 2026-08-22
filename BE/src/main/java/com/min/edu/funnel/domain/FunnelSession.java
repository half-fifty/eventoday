package com.min.edu.funnel.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일일 배치가 FunnelAction(Elasticsearch)을 session_id 기준으로 묶어 사후 계산한 세션 요약.
 * 실시간으로 갱신되지 않는다 (technical-design.md 참고).
 */
@Entity
@Table(
        name = "funnel_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_funnel_session_session_id",
                columnNames = "session_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FunnelSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 원본 세션 UUID(36자) + ":" + event_id 조합으로 저장되므로 UUID 길이보다 여유를 둔다.
    @Column(name = "session_id", nullable = false, length = 80)
    private String sessionId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "visitor_key", nullable = false, length = 100)
    private String visitorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_step_reached", nullable = false, length = 50)
    private FunnelStep maxStepReached;

    @Column(name = "is_dropped", nullable = false)
    private boolean dropped;

    @Column(name = "is_returning_visitor", nullable = false)
    private boolean returningVisitor;

    @Column(name = "is_booth_explored", nullable = false)
    private boolean boothExplored;

    @Column(name = "is_step_skipped", nullable = false)
    private boolean stepSkipped;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "last_action_at", nullable = false)
    private OffsetDateTime lastActionAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static FunnelSession create(
            String sessionId,
            Long eventId,
            String visitorKey,
            FunnelStep maxStepReached,
            boolean dropped,
            boolean returningVisitor,
            boolean boothExplored,
            boolean stepSkipped,
            OffsetDateTime startedAt,
            OffsetDateTime lastActionAt,
            OffsetDateTime now) {
        return FunnelSession.builder()
                .sessionId(sessionId)
                .eventId(eventId)
                .visitorKey(visitorKey)
                .maxStepReached(maxStepReached)
                .dropped(dropped)
                .returningVisitor(returningVisitor)
                .boothExplored(boothExplored)
                .stepSkipped(stepSkipped)
                .startedAt(startedAt)
                .lastActionAt(lastActionAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
