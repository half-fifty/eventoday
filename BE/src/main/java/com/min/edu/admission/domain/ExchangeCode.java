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

// exchange_code_request_id / ticket_order_id 상호배타 CHECK 제약은 마이그레이션(V1)에서 DB 레벨로 강제됩니다.
@Entity
@Table(name = "exchange_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExchangeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "exchange_code_request_id")
    private Long exchangeCodeRequestId;

    @Column(name = "ticket_order_id")
    private Long ticketOrderId;

    @Column(name = "holder_member_id")
    private Long holderMemberId;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeCodeStatus status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "redeemed_at")
    private OffsetDateTime redeemedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static ExchangeCode createForTicketOrder(
            Long eventId,
            Long ticketOrderId,
            Long holderMemberId,
            String code,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        return ExchangeCode.builder()
            .eventId(eventId)
            .ticketOrderId(ticketOrderId)
            .holderMemberId(holderMemberId)
            .code(code)
            .status(ExchangeCodeStatus.ISSUED)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public boolean isRedeemed() {
        return status == ExchangeCodeStatus.REDEEMED;
    }

    public boolean isCancelled() {
        return status == ExchangeCodeStatus.CANCELLED;
    }

    public void cancel(OffsetDateTime now) {
        if (isRedeemed()) {
            throw new IllegalStateException("Redeemed exchange code cannot be cancelled.");
        }

        if (isCancelled()) {
            return;
        }

        this.status = ExchangeCodeStatus.CANCELLED;
        this.updatedAt = now;
    }
}
