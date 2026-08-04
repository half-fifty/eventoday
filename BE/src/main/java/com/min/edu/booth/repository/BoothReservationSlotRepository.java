package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BoothReservationSlotRepository extends JpaRepository<BoothReservationSlot, Long> {

    // 부스의 모든 시간대 조회
    List<BoothReservationSlot> findAllByBoothId(Long boothId);

    // 부스의 특정 시간대 존재 여부 (중복 방지)
    boolean existsByBoothIdAndStartAt(Long boothId, OffsetDateTime startAt);
}