package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
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
            // 3. 조회 후 저장(read-then-write) 대신 조건부 UPDATE로 원자적으로 전환한다.
            //    스케줄러 실행이 겹치거나(fixedDelay라 겹칠 일은 적지만) 인스턴스가 여러 개 떠도,
            //    RESERVED 상태인 건만 정확히 한 번 갱신 건수 1을 받는다 — 갱신 건수 0이면 이미
            //    다른 실행이 처리한 것이므로 슬롯 복원도 건너뛴다(이중 복원 방지).
            int updated = reservationRepository.markNoShowIfReserved(reservation.getId(), now);
            if (updated == 0) {
                continue;
            }

            // 4. 슬롯 빈자리 복원 (잠금 적용) — 위에서 이긴 실행만 도달한다.
            BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                    .orElse(null);

            if (slot != null) {
                slot.decrementReservedCount(reservation.getPartySize());
                slotRepository.saveAndFlush(slot);
            }
        }
    }
}