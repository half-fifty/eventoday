package com.min.edu.advertisement.domain;

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

// event_id/booth_id 상호배타, booth_id 유무에 따른 payment_order_id 제약은 마이그레이션(V1)에서 DB 레벨로 강제됩니다.
@Entity
@Table(name = "advertisements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "booth_id")
    private Long boothId;

    @Column(name = "applicant_organization_id", nullable = false)
    private Long applicantOrganizationId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "banner_file_id")
    private Long bannerFileId;

    @Column(name = "ad_text", length = 300)
    private String adText;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AdvertisementStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void assignPaymentOrder(Long paymentOrderId, OffsetDateTime now) {
        if (eventId == null || boothId != null || this.paymentOrderId != null) {
            throw new IllegalStateException("행사 광고에만 결제 주문을 연결할 수 있습니다.");
        }
        this.paymentOrderId = paymentOrderId;
        this.updatedAt = now;
    }

    public void markPaid(OffsetDateTime now) {
        if (status != AdvertisementStatus.PAYMENT_PENDING || paymentOrderId == null) {
            throw new IllegalStateException("결제 대기 중인 행사 광고가 아닙니다.");
        }
        this.status = AdvertisementStatus.PAID;
        this.updatedAt = now;
    }

    public void update(Long bannerFileId, String adText, OffsetDateTime startAt,
            OffsetDateTime endAt, OffsetDateTime now) {
        if (status != AdvertisementStatus.PAYMENT_PENDING
                && status != AdvertisementStatus.REVIEW_PENDING) {
            throw new IllegalStateException("심사 전 광고만 수정할 수 있습니다.");
        }
        this.bannerFileId = bannerFileId;
        this.adText = adText;
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    public void updateCreative(Long bannerFileId, String adText, OffsetDateTime now) {
        if (status != AdvertisementStatus.SCHEDULED && status != AdvertisementStatus.ACTIVE) {
            throw new IllegalStateException("승인되어 노출 예정이거나 노출 중인 광고만 콘텐츠를 수정할 수 있습니다.");
        }
        this.bannerFileId = bannerFileId;
        this.adText = adText;
        this.updatedAt = now;
    }

    public void approve(Long reviewerId, OffsetDateTime now) {
        if (status != AdvertisementStatus.REVIEW_PENDING && status != AdvertisementStatus.PAID) {
            throw new IllegalStateException("심사 대기 또는 결제 완료 광고만 승인할 수 있습니다.");
        }
        if (!endAt.isAfter(now)) {
            throw new IllegalStateException("종료된 광고는 승인할 수 없습니다.");
        }
        this.status = startAt.isAfter(now) ? AdvertisementStatus.SCHEDULED : AdvertisementStatus.ACTIVE;
        this.reviewedBy = reviewerId;
        this.approvedAt = now;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void reject(Long reviewerId, String reason, OffsetDateTime now) {
        if (status != AdvertisementStatus.REVIEW_PENDING && status != AdvertisementStatus.PAID) {
            throw new IllegalStateException("심사 가능한 광고가 아닙니다.");
        }
        this.status = AdvertisementStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        if (status == AdvertisementStatus.ACTIVE || status == AdvertisementStatus.ENDED
                || status == AdvertisementStatus.CANCELLED) {
            throw new IllegalStateException("현재 상태의 광고는 취소할 수 없습니다.");
        }
        this.status = AdvertisementStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void activate(OffsetDateTime now) {
        if (status != AdvertisementStatus.SCHEDULED) {
            throw new IllegalStateException("예약된 광고만 활성화할 수 있습니다.");
        }
        this.status = AdvertisementStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void end(OffsetDateTime now) {
        if (status != AdvertisementStatus.ACTIVE && status != AdvertisementStatus.SCHEDULED) {
            throw new IllegalStateException("승인된 광고만 종료할 수 있습니다.");
        }
        this.status = AdvertisementStatus.ENDED;
        this.updatedAt = now;
    }
}
