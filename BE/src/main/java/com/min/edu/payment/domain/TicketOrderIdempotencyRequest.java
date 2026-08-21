package com.min.edu.payment.domain;

import java.time.OffsetDateTime;

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
@Table(name = "ticket_order_idempotency_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TicketOrderIdempotencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketOrderIdempotencyStatus status;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "ticket_order_id")
    private Long ticketOrderId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    public static TicketOrderIdempotencyRequest processing(
            String idempotencyKey,
            String requestHash,
            Long eventId,
            OffsetDateTime now,
            OffsetDateTime expiresAt) {
        return TicketOrderIdempotencyRequest.builder()
            .idempotencyKey(idempotencyKey)
            .requestHash(requestHash)
            .status(TicketOrderIdempotencyStatus.PROCESSING)
            .eventId(eventId)
            .createdAt(now)
            .updatedAt(now)
            .expiresAt(expiresAt)
            .build();
    }

    public boolean hasDifferentRequestHash(String requestHash) {
        return !this.requestHash.equals(requestHash);
    }

    public boolean isProcessing() {
        return status == TicketOrderIdempotencyStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == TicketOrderIdempotencyStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TicketOrderIdempotencyStatus.FAILED;
    }

    public boolean isProcessingExpired(OffsetDateTime now) {
        return isProcessing() && expiresAt != null && !expiresAt.isAfter(now);
    }

    public void restartProcessing(OffsetDateTime now, OffsetDateTime expiresAt) {
        this.status = TicketOrderIdempotencyStatus.PROCESSING;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
    }

    public void complete(Long paymentOrderId, Long ticketOrderId, OffsetDateTime now) {
        this.status = TicketOrderIdempotencyStatus.COMPLETED;
        this.paymentOrderId = paymentOrderId;
        this.ticketOrderId = ticketOrderId;
        this.updatedAt = now;
        this.completedAt = now;
        this.expiresAt = null;
    }

    public void fail(OffsetDateTime now) {
        if (isCompleted()) {
            return;
        }
        this.status = TicketOrderIdempotencyStatus.FAILED;
        this.updatedAt = now;
        this.expiresAt = null;
    }
}
