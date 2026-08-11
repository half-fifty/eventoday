package com.min.edu.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "platform_audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlatformAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_member_id", nullable = false)
    private Long actorMemberId;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(name = "target_name", nullable = false, length = 200)
    private String targetName;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
