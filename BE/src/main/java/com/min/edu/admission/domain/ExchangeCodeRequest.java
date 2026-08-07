package com.min.edu.admission.domain;

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
@Table(name = "exchange_code_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExchangeCodeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExchangeCodeRequestStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "emailed_at")
    private OffsetDateTime emailedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static ExchangeCodeRequest create(
            Long eventId,
            Long requestedBy,
            Integer requestedQuantity,
            String purpose,
            OffsetDateTime now) {
        return ExchangeCodeRequest.builder()
            .eventId(eventId)
            .requestedBy(requestedBy)
            .requestedQuantity(requestedQuantity)
            .purpose(purpose)
            .status(ExchangeCodeRequestStatus.REQUESTED)
            .createdAt(now)
            .build();
    }

    public boolean isRequested() {
        return status == ExchangeCodeRequestStatus.REQUESTED;
    }

    public boolean isApproved() {
        return status == ExchangeCodeRequestStatus.APPROVED;
    }

    public boolean isIssued() {
        return status == ExchangeCodeRequestStatus.ISSUED;
    }

    public void approve(Long reviewerId, OffsetDateTime now) {
        if (!isRequested()) {
            throw new IllegalStateException("Exchange code request is not reviewable.");
        }

        this.status = ExchangeCodeRequestStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = null;
    }

    public void reject(Long reviewerId, String reason, OffsetDateTime now) {
        if (!isRequested()) {
            throw new IllegalStateException("Exchange code request is not reviewable.");
        }

        this.status = ExchangeCodeRequestStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    public void issue() {
        if (!isApproved()) {
            throw new IllegalStateException("Exchange code request is not issuable.");
        }

        this.status = ExchangeCodeRequestStatus.ISSUED;
    }

    public void markEmailed(OffsetDateTime emailedAt) {
        if (!isIssued()) {
            throw new IllegalStateException("Exchange code request is not issued.");
        }

        this.emailedAt = emailedAt;
    }
}
