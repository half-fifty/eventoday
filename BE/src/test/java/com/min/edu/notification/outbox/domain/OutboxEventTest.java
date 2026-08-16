package com.min.edu.notification.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void create_startsAsPendingWithZeroRetryCount() {
        OffsetDateTime now = OffsetDateTime.now();

        OutboxEvent event = OutboxEvent.create("42", "{}", now);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void markPublished_movesToPublishedAndClearsLastError() {
        OutboxEvent event = OutboxEvent.create("42", "{}", OffsetDateTime.now());
        OffsetDateTime publishedAt = OffsetDateTime.now();

        event.markPublished(publishedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void markFailedAttempt_belowMaxRetries_staysPendingWithBackoffAndIncrementsRetryCount() {
        OutboxEvent event = OutboxEvent.create("42", "{}", OffsetDateTime.now());
        OffsetDateTime now = OffsetDateTime.now();

        event.markFailedAttempt(now, "타임아웃");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("타임아웃");
        assertThat(event.getNextAttemptAt()).isAfter(now);
    }

    @Test
    void markFailedAttempt_fifthFailure_stillSchedulesOneMoreRetry() {
        OutboxEvent event = OutboxEvent.create("42", "{}", OffsetDateTime.now());
        OffsetDateTime now = OffsetDateTime.now();

        for (int attempt = 0; attempt < 5; attempt++) {
            event.markFailedAttempt(now, "실패 " + attempt);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    @Test
    void markFailedAttempt_sixthFailure_movesToFailed() {
        OutboxEvent event = OutboxEvent.create("42", "{}", OffsetDateTime.now());
        OffsetDateTime now = OffsetDateTime.now();

        for (int attempt = 0; attempt < 6; attempt++) {
            event.markFailedAttempt(now, "실패 " + attempt);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(6);
    }

    @Test
    void markFailedAttempt_clearsLeaseSoTheRowCanBeReclaimed() {
        OutboxEvent event = OutboxEvent.create("42", "{}", OffsetDateTime.now());

        event.markFailedAttempt(OffsetDateTime.now(), "타임아웃");

        assertThat(event.getLeaseExpiresAt()).isNull();
    }
}
