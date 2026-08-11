package com.min.edu.booth.domain;

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

// member_id + booth_id 유일성은 status = 'RESERVED'인 행에만 적용되는 부분 유니크 인덱스
// (uk_booth_reservations_active, V18)로 관리된다. JPA @UniqueConstraint로는 조건부 인덱스를
// 표현할 수 없어 여기서는 선언하지 않는다.
@Entity
@Table(name = "booth_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;


    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "booth_reservation_slot_id", nullable = false)
    private Long boothReservationSlotId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BoothReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private OffsetDateTime reservedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "no_show_at")
    private OffsetDateTime noShowAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void updateStatus(BoothReservationStatus status) {
        this.status = status;
    }

    public void updateCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public void updateCheckedInAt(OffsetDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public void updateNoShowAt(OffsetDateTime noShowAt) {
        this.noShowAt = noShowAt;
    }

    public void updateUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
