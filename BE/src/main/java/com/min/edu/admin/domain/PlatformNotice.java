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

/**
 * 플랫폼(사이트 전체) 공지사항
 * 행사 단위 공지는 EventContent가 담당하고, 이 엔티티는 행사와 무관한 사이트 공지만 다룬다.
 */
@Entity
@Table(name = "platform_notices")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlatformNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_member_id", nullable = false)
    private Long authorMemberId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 공지 생성 팩토리 메서드 (EventContent.create와 동일한 패턴) */
    public static PlatformNotice create(
            Long authorMemberId, String title, String content, boolean pinned, OffsetDateTime now) {
        return PlatformNotice.builder()
                .authorMemberId(authorMemberId)
                .title(title)
                .content(content)
                .pinned(pinned)
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 공지 수정 메서드 (게시 시각 publishedAt은 최초 등록 시점을 유지한다) */
    public void update(String title, String content, boolean pinned, OffsetDateTime now) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = now;
    }
}
