package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReviewSummary;
import com.min.edu.booth.repository.BoothReviewSummaryRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// BoothReviewSummaryService에서 LLM 호출이 끝난 뒤에만 짧은 쓰기 트랜잭션을 여는 용도로 분리했다.
// (같은 클래스의 @Transactional 메서드를 this로 호출하면 프록시를 안 거쳐 트랜잭션이 적용되지 않는다)
@Component
@RequiredArgsConstructor
class BoothReviewSummaryWriter {

    private final BoothReviewSummaryRepository boothReviewSummaryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BoothReviewSummary save(
            Long boothId, String summary, int reviewCountAtSummary, OffsetDateTime lastReviewUpdatedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        BoothReviewSummary entity = boothReviewSummaryRepository.findById(boothId)
            .map(existing -> {
                existing.update(summary, reviewCountAtSummary, lastReviewUpdatedAt, now);
                return existing;
            })
            .orElseGet(() -> BoothReviewSummary.builder()
                .boothId(boothId)
                .summary(summary)
                .reviewCountAtSummary(reviewCountAtSummary)
                .lastReviewUpdatedAt(lastReviewUpdatedAt)
                .generatedAt(now)
                .build());
        return boothReviewSummaryRepository.save(entity);
    }
}
