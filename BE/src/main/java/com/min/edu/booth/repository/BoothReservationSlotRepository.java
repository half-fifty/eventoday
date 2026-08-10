package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface BoothReservationSlotRepository extends JpaRepository<BoothReservationSlot, Long> {

    // ===== 기존 메서드 =====
    // (기존 메서드들이 있으면 유지)

    // ===== 새로운 메서드 =====

    /**
     * 1. 부스의 특정 시작 시간 슬롯 존재 확인
     *
     * @param boothId 부스 ID
     * @param startAt 시작 시간
     * @return 같은 시간의 슬롯이 있으면 true
     */
    boolean existsByBoothIdAndStartAt(Long boothId, OffsetDateTime startAt);

    /**
     * 2. 비관적 잠금으로 슬롯 조회
     *
     * @param id 슬롯 ID
     * @return Optional<BoothReservationSlot>
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT brs FROM BoothReservationSlot brs WHERE brs.id = :id")
    Optional<BoothReservationSlot> findByIdWithLock(@Param("id") Long id);

    /**
     * 3. 특정 슬롯 제외하고 시간 충돌 확인
     *
     * @param boothId 부스 ID
     * @param startAt 시작 시간
     * @param idNot 제외할 슬롯 ID
     * @return 다른 슬롯에서 같은 시간이 있으면 true
     */
    boolean existsByBoothIdAndStartAtAndIdNot(Long boothId, OffsetDateTime startAt, Long idNot);

    /**
     * 4. 부스의 예약 가능 슬롯 존재 확인 (MobileGuideService용)
     *
     * @param boothId 부스 ID
     * @return 예약 가능한 슬롯이 있으면 true
     */
    @Query("SELECT COUNT(brs) > 0 FROM BoothReservationSlot brs " +
            "WHERE brs.boothId = :boothId " +
            "AND brs.status = 'OPEN' " +
            "AND brs.reservedCount < brs.capacity")
    boolean existsByBoothIdAndAvailableSlots(@Param("boothId") Long boothId);
}