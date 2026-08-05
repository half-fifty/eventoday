package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoothReservationRepository extends JpaRepository<BoothReservation, Long> {

    // 회원의 특정 부스 예약 조회
    Optional<BoothReservation> findByIdAndMemberId(Long id, Long memberId);

    // 회원의 같은 부스 예약 존재 여부
    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);
}