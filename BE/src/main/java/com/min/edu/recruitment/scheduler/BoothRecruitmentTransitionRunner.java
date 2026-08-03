package com.min.edu.recruitment.scheduler;

import java.time.OffsetDateTime;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class BoothRecruitmentTransitionRunner {

    private final BoothRecruitmentRepository boothRecruitmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transition(Long recruitmentId, BoothRecruitmentStatus newStatus, OffsetDateTime now) {
        BoothRecruitment recruitment = boothRecruitmentRepository.findById(recruitmentId).orElse(null);

        if (recruitment == null) {
            return;
        }

        try {
            recruitment.changeStatus(newStatus, now);
            boothRecruitmentRepository.saveAndFlush(recruitment);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn(
                "모집 공고 {} 상태 자동 전환({})이 동시 변경과 충돌해 건너뜁니다. 다음 실행에서 재시도합니다.",
                recruitmentId,
                newStatus
            );
        }
    }
}
