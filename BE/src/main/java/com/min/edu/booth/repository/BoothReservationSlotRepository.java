package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
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
     * 2. 비관적 잠금으로 슬롯 조회 (동시성 제어)
     * - CREATE/UPDATE 시 Race Condition 방지
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
     * 4. 부스의 예약 가능 슬롯 조회 (DB 기준)
     *
     * ⚠️ 주의: 이 메서드는 DB의 가용 슬롯만 반영합니다.
     * Redis 선점 수를 고려한 실제 예약 가능 상태는 Service에서 처리하세요.
     *
     * 실제 가용 = capacity - reservedCount - Redis_selected_count
     *           ↑ DB          ↑ DB         ↑ Service에서 조회해서 뺌
     *
     *
     */
    @Query("SELECT COUNT(brs) > 0 FROM BoothReservationSlot brs " +
            "WHERE brs.boothId = :boothId " +
            "AND brs.status = 'OPEN' " +
            "AND brs.reservedCount < brs.capacity")
    boolean existsByBoothIdAndAvailableSlots(@Param("boothId") Long boothId);

    /**
     * 5. 부스의 모든 OPEN 슬롯 조회 (Service에서 Redis 선점 제외용)
     *
     * @param boothId 부스 ID
     * @return 부스의 모든 OPEN 슬롯 리스트
     */
    @Query("SELECT brs FROM BoothReservationSlot brs " +
            "WHERE brs.boothId = :boothId " +
            "AND brs.status = 'OPEN'")
    List<BoothReservationSlot> findAllOpenSlotsByBoothId(@Param("boothId") Long boothId);

    /**
     * 6. 부스의 전체 슬롯 조회 (OPEN/CLOSED 모두, 시작시간순)
     * - 예약 화면의 시간대 선택 목록, 운영자의 슬롯 관리 목록에서 사용
     */
    List<BoothReservationSlot> findAllByBoothIdOrderByStartAtAsc(Long boothId);
}