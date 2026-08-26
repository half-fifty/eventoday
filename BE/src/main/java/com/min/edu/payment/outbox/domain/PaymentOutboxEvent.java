package com.min.edu.payment.outbox.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentOutboxEvent {

    public static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private PaymentOutboxEventType eventType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentOutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "available_at", nullable = false)
    private OffsetDateTime availableAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private OffsetDateTime leaseUntil;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    public static PaymentOutboxEvent ticketReservationConfirmation(
            String aggregateId,
            String payload,
            OffsetDateTime now) {
        return PaymentOutboxEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL)
            .aggregateId(aggregateId)
            .payload(payload)
            .status(PaymentOutboxEventStatus.PENDING)
            .retryCount(0)
            .availableAt(now)
            .createdAt(now)
            .build();
    }

    public void markPublished(OffsetDateTime now) {
        this.status = PaymentOutboxEventStatus.PUBLISHED;
        this.publishedAt = now;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastError = null;
    }

    public void markRetry(OffsetDateTime nextAvailableAt, String lastError) {
        this.retryCount += 1;
        this.status = PaymentOutboxEventStatus.PENDING;
        this.availableAt = nextAvailableAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastError = sanitize(lastError);
    }

    public void markFailed(String lastError) {
        this.retryCount += 1;
        this.status = PaymentOutboxEventStatus.FAILED;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastError = sanitize(lastError);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > LAST_ERROR_MAX_LENGTH
            ? value.substring(0, LAST_ERROR_MAX_LENGTH)
            : value;
    }
}
