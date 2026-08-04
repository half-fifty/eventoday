package com.min.edu.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "notification_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static Notification create(
            Long memberId,
            NotificationType notificationType,
            String referenceType,
            Long referenceId,
            String title,
            String content) {
        return Notification.builder()
                .memberId(memberId)
                .notificationType(notificationType)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .title(title)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }

    public boolean isRead() {
        return this.readAt != null;
    }
}
