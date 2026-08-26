package com.min.edu.booth.domain;

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

// booth_id + admission_ticket_id 유니크 제약(마이그레이션)으로 "부스 하나당 같은 입장권으로는
// 한 번만 체크인 가능"을 DB 레벨에서 강제한다. ExchangeCode(1회성 교환코드)를 쓰던 예전 버전과
// 달리 AdmissionTicket.qrToken은 게이트 입장 후에도 소모되지 않아 부스마다 반복 인식이 가능하다.
@Entity
@Table(name = "booth_qr_scans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothQrScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "admission_ticket_id", nullable = false)
    private Long admissionTicketId;

    @Column(name = "scanned_at", nullable = false)
    private OffsetDateTime scannedAt;
}
