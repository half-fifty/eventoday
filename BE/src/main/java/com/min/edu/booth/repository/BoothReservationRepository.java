package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoothReservationRepository extends JpaRepository<BoothReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BoothReservation r where r.id = :id and r.memberId = :memberId")
    Optional<BoothReservation> findByIdAndMemberIdWithLock(@Param("id") Long id, @Param("memberId") Long memberId);

    // 운영자의 수동 출석 체크용 (memberId 무관하게 예약 단건 잠금 조회)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BoothReservation r where r.id = :id")
    Optional<BoothReservation> findByIdWithLock(@Param("id") Long id);

    // 회원의 특정 부스 예약 조회
    Optional<BoothReservation> findByIdAndMemberId(Long id, Long memberId);

    // 회원이 이 부스를 예약한 적이 있는지 여부(상태 무관) - 한 번 예약(취소 포함)하면 그 부스는 재예약 불가
    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);

    // 회원의 특정 부스 예약 조회 (상태 무관) - 취소된 예약이어도 "이미 예약했던 부스" 상태를 보여주기 위해 사용
    Optional<BoothReservation> findByMemberIdAndBoothId(Long memberId, Long boothId);

    // 회원의 같은 부스 진행 중(RESERVED) 예약 존재 여부
    boolean existsByMemberIdAndBoothIdAndStatus(Long memberId, Long boothId, BoothReservationStatus status);

    // 회원의 특정 부스 예약 조회 (내 예약 상태 확인용)
    Optional<BoothReservation> findByMemberIdAndBoothIdAndStatus(Long memberId, Long boothId, BoothReservationStatus status);

    // 슬롯 삭제 가능 여부 확인용 (취소된 예약도 FK로 슬롯을 참조하므로 상태와 무관하게 확인한다)
    boolean existsByBoothReservationSlotId(Long boothReservationSlotId);

    // 운영자용 예약 목록 (부스별 전체 예약자 조회)
    List<BoothReservation> findAllByBoothIdOrderByReservedAtDesc(Long boothId);

    // 회원의 모든 부스 예약 목록 (행사 전체에 걸쳐, 최신순, 페이징) - "내 예약 목록" 화면용.
    // reservedAt만으로 정렬하면 같은 시각에 예약된 건들의 순서가 페이지마다 안정적이지 않을 수 있어
    // id를 보조 정렬 키로 추가한다.
    Page<BoothReservation> findAllByMemberIdOrderByReservedAtDescIdDesc(Long memberId, Pageable pageable);

    @Query("select r from BoothReservation r join BoothReservationSlot s on r.boothReservationSlotId = s.id " +
            "where s.endAt <= :now and r.status = 'RESERVED'")
    List<BoothReservation> findExpiredReservations(@Param("now") OffsetDateTime now);

    // 체크인은 했지만 예약 시간대가 끝난 예약 (이용 완료 자동 전환 대상)
    @Query("select r from BoothReservation r join BoothReservationSlot s on r.boothReservationSlotId = s.id " +
            "where s.endAt <= :now and r.status = 'CHECKED_IN'")
    List<BoothReservation> findCompletableReservations(@Param("now") OffsetDateTime now);

    // 조회 후 저장(read-then-write) 대신 조건부 UPDATE로 원자적으로 상태를 전환한다.
    // 여러 스케줄러 실행이 겹쳐도 정확히 하나의 실행만 갱신 건수 1을 받아 처리하게 된다.
    @Modifying
    @Query("update BoothReservation r set r.status = :newStatus, r.updatedAt = :now " +
            "where r.id = :id and r.status = :expectedStatus")
    int updateStatusIfCurrentStatus(
            @Param("id") Long id,
            @Param("expectedStatus") BoothReservationStatus expectedStatus,
            @Param("newStatus") BoothReservationStatus newStatus,
            @Param("now") OffsetDateTime now);

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