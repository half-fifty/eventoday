package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothNoShowService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;

    // 5분마다 실행 (5분 = 300000ms)
    @Scheduled(fixedDelay = 300000)
    public void processNoShows() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 시간이 지난 예약 조회 (status = RESERVED)
        List<BoothReservation> expiredReservations = reservationRepository.findExpiredReservations(now);

        // 2. 각 예약에 대해 노쇼 처리
        for (BoothReservation reservation : expiredReservations) {
            // 3. 예약 상태 변경: RESERVED → NO_SHOW
            reservation.updateStatus(BoothReservationStatus.NO_SHOW);
            reservation.updateNoShowAt(now);
            reservation.updateUpdatedAt(now);
            reservationRepository.saveAndFlush(reservation);

            // 4. 슬롯 빈자리 복원
            BoothReservationSlot slot = slotRepository.findById(reservation.getBoothReservationSlotId())
                    .orElse(null);
            if (slot != null) {
                slot.decrementReservedCount(reservation.getPartySize());
                slotRepository.saveAndFlush(slot);
            }
        }
    }
}