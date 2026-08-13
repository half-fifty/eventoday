package com.min.edu.notification.outbox.domain;

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

/**
 * 비즈니스 트랜잭션 커밋 이후 카프카로 내보내야 할 이벤트를 임시로 보관하는 발송 대기함.
 * 커밋과 같은 트랜잭션에서 이 행을 남기는 대신, 이미 커밋된 트랜잭션 뒤에
 * (AFTER_COMMIT 리스너에서) 자체 트랜잭션으로 기록한다 — 실제 카프카 호출이라는
 * 외부 네트워크 작업을 순수 DB insert로 바꿔서 실패 가능성을 크게 낮추는 것이 목적이다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OutboxEvent {

    private static final int MAX_RETRY_COUNT = 5;
    private static final long MAX_BACKOFF_SECONDS = 60;
    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "message_key", nullable = false, length = 255)
    private String messageKey;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public static OutboxEvent create(String messageKey, String payload, OffsetDateTime now) {
        return OutboxEvent.builder()
            .messageKey(messageKey)
            .payload(payload)
            .status(OutboxEventStatus.PENDING)
            .retryCount(0)
            .nextAttemptAt(now)
            .createdAt(now)
            .build();
    }

    public void markPublished(OffsetDateTime now) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
    }

    public void markFailedAttempt(OffsetDateTime now, String errorMessage) {
        this.retryCount += 1;
        this.lastError = truncate(errorMessage);

        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxEventStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plusSeconds(backoffSeconds(this.retryCount));
        }
    }

    private long backoffSeconds(int retryCount) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << retryCount);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > LAST_ERROR_MAX_LENGTH
            ? value.substring(0, LAST_ERROR_MAX_LENGTH)
            : value;
    }
}
