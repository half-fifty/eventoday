package com.min.edu.recruitment.scheduler;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class BoothRecruitmentTransitionRunner {

    private final BoothRecruitmentRepository boothRecruitmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transition(
            Long recruitmentId,
            BoothRecruitmentStatus expectedStatus,
            BoothRecruitmentStatus newStatus,
            OffsetDateTime now) {
        BoothRecruitment recruitment = boothRecruitmentRepository.findById(recruitmentId).orElse(null);

        if (recruitment == null || recruitment.getStatus() != expectedStatus) {
            return;
        }

        recruitment.changeStatus(newStatus, now);
        boothRecruitmentRepository.saveAndFlush(recruitment);
    }
}
