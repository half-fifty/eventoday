package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservationSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoothReservationSlotRepository extends JpaRepository<BoothReservationSlot, Long> {

    // 부스의 모든 시간대 조회
    List<BoothReservationSlot> findAllByBoothId(Long boothId);

    // 부스의 특정 시간대 존재 여부 (중복 방지)
    boolean existsByBoothIdAndStartAt(Long boothId, OffsetDateTime startAt);

    // 수정 시 자기 자신을 제외한 중복 시간대 존재 여부
    boolean existsByBoothIdAndStartAtAndIdNot(Long boothId, OffsetDateTime startAt, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BoothReservationSlot s where s.id = :id")
    Optional<BoothReservationSlot> findByIdWithLock(@Param("id") Long id);

}