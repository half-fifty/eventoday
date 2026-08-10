package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoothReservationRepository extends JpaRepository<BoothReservation, Long> {


    Page<BoothReservation> findByBoothIdOrderByCreatedAtDesc(Long boothId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BoothReservation r where r.id = :id and r.memberId = :memberId")
    Optional<BoothReservation> findByIdAndMemberIdWithLock(@Param("id") Long id, @Param("memberId") Long memberId);

    // 회원의 특정 부스 예약 조회
    Optional<BoothReservation> findByIdAndMemberId(Long id, Long memberId);

    // 회원의 같은 부스 예약 존재 여부
    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);

    @Query("select r from BoothReservation r join BoothReservationSlot s on r.boothReservationSlotId = s.id " +
            "where s.endAt <= :now and r.status = 'RESERVED'")
    List<BoothReservation> findExpiredReservations(@Param("now") OffsetDateTime now);

    // 회원의 마지막 방문 부스 조회 (가장 최근)
    Optional<BoothReservation> findFirstByMemberIdAndStatusOrderByCheckedInAtDesc(
            Long memberId, BoothReservationStatus status);

    // ===== CheckIn용 메서드 (WBS-155) =====
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT br FROM BoothReservation br WHERE br.memberId = :memberId AND br.boothId = :boothId")
    Optional<BoothReservation> findByMemberIdAndBoothIdWithLock(
            @Param("memberId") Long memberId,
            @Param("boothId") Long boothId
    );
}