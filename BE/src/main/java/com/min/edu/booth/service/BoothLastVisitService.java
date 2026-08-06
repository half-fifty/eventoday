package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.LastVisitedBoothResponse;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothLastVisitService {

    private final BoothReservationRepository reservationRepository;

    public LastVisitedBoothResponse getLastVisitedBooth(Long memberId) {
        // 1. 회원의 가장 최근 방문 부스 조회
        BoothReservation reservation = reservationRepository
                .findFirstByMemberIdAndStatusOrderByCheckedInAtDesc(memberId, BoothReservationStatus.CHECKED_IN)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2. 응답 반환
        return toResponse(reservation);
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