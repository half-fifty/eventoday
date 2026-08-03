package com.min.edu.recruitment.scheduler;

import java.time.OffsetDateTime;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
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
            transitionOne(recruitment.getId(), BoothRecruitmentStatus.BEFORE_OPEN, BoothRecruitmentStatus.OPEN, now);
        }
    }

    private void closeRecruitmentsThatHaveEnded(OffsetDateTime now) {
        for (BoothRecruitment recruitment : boothRecruitmentRepository
                .findAllByStatusAndRecruitmentEndAtLessThanEqual(BoothRecruitmentStatus.OPEN, now)) {
            transitionOne(recruitment.getId(), BoothRecruitmentStatus.OPEN, BoothRecruitmentStatus.CLOSED, now);
        }
    }

    private void transitionOne(
            Long recruitmentId,
            BoothRecruitmentStatus expectedStatus,
            BoothRecruitmentStatus newStatus,
            OffsetDateTime now) {
        try {
            transitionRunner.transition(recruitmentId, expectedStatus, newStatus, now);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn(
                "모집 공고 {} 상태 자동 전환({})이 동시 변경과 충돌해 건너뜁니다. 다음 실행에서 재시도합니다.",
                recruitmentId,
                newStatus
            );
        }
    }
}
