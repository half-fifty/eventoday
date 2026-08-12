package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.repository.BoothReservationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 체크인(CHECKED_IN)한 예약 중 예약 시간대가 끝난 건을 이용 완료(COMPLETED)로 자동 전환한다.
// BoothNoShowService와 동일한 주기 스케줄러 패턴.
@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationCompletionService {

    private final BoothReservationRepository reservationRepository;

    // 5분마다 실행 (5분 = 300000ms)
    @Scheduled(fixedDelay = 300000)
    public void processCompletions() {
        OffsetDateTime now = OffsetDateTime.now();

        List<BoothReservation> completableReservations = reservationRepository.findCompletableReservations(now);

        for (BoothReservation reservation : completableReservations) {
            if (reservation.getStatus() != BoothReservationStatus.CHECKED_IN) {
                continue;  // 다른 스레드가 이미 처리했으면 skip
            }

            reservation.updateStatus(BoothReservationStatus.COMPLETED);
            reservation.updateUpdatedAt(now);
            reservationRepository.saveAndFlush(reservation);
        }
    }
}
