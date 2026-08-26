package com.min.edu.payment.domain;

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

@Entity
@Table(name = "payment_virtual_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentVirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "bank_code", length = 10)
    private String bankCode;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "webhook_secret_hash", nullable = false, length = 64)
    private String webhookSecretHash;

    @Column(name = "toss_status", nullable = false, length = 30)
    private String tossStatus;

    @Column(name = "deposited_at")
    private OffsetDateTime depositedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static PaymentVirtualAccount create(
            Long paymentId,
            String bankCode,
            String accountNumber,
            String customerName,
            OffsetDateTime dueAt,
            String webhookSecretHash,
            String tossStatus,
            OffsetDateTime now) {
        return PaymentVirtualAccount.builder()
            .paymentId(paymentId)
            .bankCode(bankCode)
            .accountNumber(accountNumber)
            .customerName(customerName)
            .dueAt(dueAt)
            .webhookSecretHash(webhookSecretHash)
            .tossStatus(tossStatus)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void markDeposited(String tossStatus, OffsetDateTime depositedAt, OffsetDateTime now) {
        this.tossStatus = tossStatus;
        this.depositedAt = depositedAt;
        this.updatedAt = now;
    }

    public void markExpired(OffsetDateTime now) {
        this.tossStatus = "EXPIRED";
        this.updatedAt = now;
    }
}
