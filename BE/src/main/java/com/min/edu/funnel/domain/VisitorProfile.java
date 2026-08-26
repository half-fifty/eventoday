package com.min.edu.funnel.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * visitorKey(로그인 시 "member:{userId}", 비로그인 시 "anon:{anonymousId}") 단위로
 * 최초/최근 방문 시각을 들고 있어, 매 세션마다 전체 FunnelAction 히스토리를 조회하지 않고도
 * 신규/재방문 여부를 판정할 수 있게 한다 (technical-design.md 참고).
 */
@Entity
@Table(
        name = "visitor_profile",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visitor_profile_visitor_key",
                columnNames = "visitor_key"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class VisitorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "visitor_key", nullable = false, length = 100)
    private String visitorKey;

    @Column(name = "anonymous_id", nullable = false, length = 100)
    private String anonymousId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static VisitorProfile create(
            String visitorKey,
            String anonymousId,
            Long userId,
            OffsetDateTime now) {
        return VisitorProfile.builder()
                .visitorKey(visitorKey)
                .anonymousId(anonymousId)
                .userId(userId)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void recordVisit(OffsetDateTime now) {
        this.lastSeenAt = now;
        this.updatedAt = now;
    }
}
