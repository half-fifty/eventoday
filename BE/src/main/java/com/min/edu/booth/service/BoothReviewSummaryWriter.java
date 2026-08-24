package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReviewSummary;
import com.min.edu.booth.domain.BoothReviewSummaryBatch;
import com.min.edu.booth.repository.BoothReviewSummaryBatchRepository;
import com.min.edu.booth.repository.BoothReviewSummaryRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
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
    private final BoothReviewSummaryBatchRepository boothReviewSummaryBatchRepository;

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

    // 배치 하나(review id 고정 범위)의 요약을 새로 만들거나(신규 배치) 갱신한다(기존 배치 내용이
    // 삭제/수정으로 바뀐 경우). existingId가 있으면 그 행을 갱신, 없으면 새로 만든다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BoothReviewSummaryBatch saveBatch(
            Long existingId,
            Long boothId,
            int batchIndex,
            long fromReviewId,
            long toReviewId,
            int reviewCount,
            String summary,
            OffsetDateTime lastReviewUpdatedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        BoothReviewSummaryBatch entity = Optional.ofNullable(existingId)
            .flatMap(boothReviewSummaryBatchRepository::findById)
            .map(existing -> {
                existing.update(reviewCount, summary, lastReviewUpdatedAt, now);
                return existing;
            })
            .orElseGet(() -> BoothReviewSummaryBatch.builder()
                .boothId(boothId)
                .batchIndex(batchIndex)
                .fromReviewId(fromReviewId)
                .toReviewId(toReviewId)
                .reviewCount(reviewCount)
                .summary(summary)
                .lastReviewUpdatedAt(lastReviewUpdatedAt)
                .generatedAt(now)
                .build());
        return boothReviewSummaryBatchRepository.save(entity);
    }

    // 범위 안의 리뷰가 전부 삭제돼(reviewCount == 0) 더 이상 의미가 없어진 배치를 제거한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatch(Long batchId) {
        boothReviewSummaryBatchRepository.deleteById(batchId);
    }
}
