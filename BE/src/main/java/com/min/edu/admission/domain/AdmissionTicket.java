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
@Table(name = "admission_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AdmissionTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "exchange_code_id", nullable = false, unique = true)
    private Long exchangeCodeId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "qr_token", nullable = false, unique = true, length = 150)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdmissionTicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    public static AdmissionTicket issue(
            Long exchangeCodeId,
            Long memberId,
            String qrToken,
            OffsetDateTime now) {
        return AdmissionTicket.builder()
            .exchangeCodeId(exchangeCodeId)
            .memberId(memberId)
            .qrToken(qrToken)
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(now)
            .build();
    }
}
