package com.min.edu.recruitment.scheduler;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoothRecruitmentStatusScheduler {

    private static final long FIXED_RATE_MILLIS = 60_000;

    private final BoothRecruitmentRepository boothRecruitmentRepository;

    @Scheduled(fixedRate = FIXED_RATE_MILLIS)
    @Transactional
    public void transitionStatuses() {
        OffsetDateTime now = OffsetDateTime.now();

        openRecruitmentsThatHaveStarted(now);
        closeRecruitmentsThatHaveEnded(now);
    }

    private void openRecruitmentsThatHaveStarted(OffsetDateTime now) {
        for (BoothRecruitment recruitment : boothRecruitmentRepository
                .findAllByStatusAndRecruitmentStartAtLessThanEqual(BoothRecruitmentStatus.BEFORE_OPEN, now)) {
            recruitment.changeStatus(BoothRecruitmentStatus.OPEN, now);
        }
    }

    private void closeRecruitmentsThatHaveEnded(OffsetDateTime now) {
        for (BoothRecruitment recruitment : boothRecruitmentRepository
                .findAllByStatusAndRecruitmentEndAtLessThanEqual(BoothRecruitmentStatus.OPEN, now)) {
            recruitment.changeStatus(BoothRecruitmentStatus.CLOSED, now);
        }
    }
}
