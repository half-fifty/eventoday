package com.min.edu.recruitment.scheduler;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoothRecruitmentStatusScheduler {

    private static final long FIXED_RATE_MILLIS = 60_000;

    private final BoothRecruitmentRepository boothRecruitmentRepository;
    private final BoothRecruitmentTransitionRunner transitionRunner;

    @Scheduled(fixedRate = FIXED_RATE_MILLIS)
    public void transitionStatuses() {
        OffsetDateTime now = OffsetDateTime.now();

        openRecruitmentsThatHaveStarted(now);
        closeRecruitmentsThatHaveEnded(now);
    }

    private void openRecruitmentsThatHaveStarted(OffsetDateTime now) {
        for (BoothRecruitment recruitment : boothRecruitmentRepository
                .findAllByStatusAndRecruitmentStartAtLessThanEqual(BoothRecruitmentStatus.BEFORE_OPEN, now)) {
            transitionRunner.transition(recruitment.getId(), BoothRecruitmentStatus.OPEN, now);
        }
    }

    private void closeRecruitmentsThatHaveEnded(OffsetDateTime now) {
        for (BoothRecruitment recruitment : boothRecruitmentRepository
                .findAllByStatusAndRecruitmentEndAtLessThanEqual(BoothRecruitmentStatus.OPEN, now)) {
            transitionRunner.transition(recruitment.getId(), BoothRecruitmentStatus.CLOSED, now);
        }
    }
}
