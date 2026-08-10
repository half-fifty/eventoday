package com.min.edu.event.domain;

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
@Table(name = "event_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "author_member_id", nullable = false)
    private Long authorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private EventContentType contentType;

    @Column(name = "resource_type", length = 30)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 20)
    private EventContentAudience audience;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 공지·자료 생성 팩토리 메서드 */
    public static EventContent create(
            Long eventId,
            Long authorMemberId,
            EventContentType contentType,
            String resourceType,
            EventContentAudience audience,
            String title,
            String content,
            Long fileId,
            String version,
            boolean pinned,
            OffsetDateTime now) {
        return EventContent.builder()
                .eventId(eventId)
                .authorMemberId(authorMemberId)
                .contentType(contentType)
                .resourceType(resourceType)
                .audience(audience)
                .title(title)
                .content(content)
                .fileId(fileId)
                .version(version)
                .pinned(pinned)
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 공지·자료 수정 메서드 */
    public void update(
            EventContentType contentType,
            String resourceType,
            EventContentAudience audience,
            String title,
            String content,
            Long fileId,
            String version,
            boolean pinned,
            OffsetDateTime now) {
        this.contentType = contentType;
        this.resourceType = resourceType;
        this.audience = audience;
        this.title = title;
        this.content = content;
        this.fileId = fileId;
        this.version = version;
        this.pinned = pinned;
        this.updatedAt = now;
    }
}
