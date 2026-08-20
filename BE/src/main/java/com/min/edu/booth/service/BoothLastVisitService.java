package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.LastVisitedBoothResponse;
import com.min.edu.booth.repository.BoothReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothLastVisitService {

    private final BoothReservationRepository reservationRepository;

    // 방문 이력이 없으면 null을 반환한다 - "아직 방문한 부스 없음"은 정상적인 빈 상태라
    // 에러(404)로 다룰 이유가 없다.
    public LastVisitedBoothResponse getLastVisitedBooth(Long memberId) {
        return reservationRepository
                .findFirstByMemberIdAndStatusOrderByCheckedInAtDesc(memberId, BoothReservationStatus.CHECKED_IN)
                .map(this::toResponse)
                .orElse(null);
    }

    private LastVisitedBoothResponse toResponse(BoothReservation reservation) {
        return LastVisitedBoothResponse.builder()
                .reservationId(reservation.getId())
                .boothId(reservation.getBoothId())
                .memberId(reservation.getMemberId())
                .checkedInAt(reservation.getCheckedInAt())
                .partySize(reservation.getPartySize())
                .build();
    }
}