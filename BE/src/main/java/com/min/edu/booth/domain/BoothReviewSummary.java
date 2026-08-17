package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booth_review_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReviewSummary {

    @Id
    @Column(name = "booth_id")
    private Long boothId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "review_count_at_summary", nullable = false)
    private Integer reviewCountAtSummary;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    /** 요약 생성 시점의 코멘트 리뷰 최신 수정 시각 — 개수는 그대로여도 내용이 바뀌면 캐시를 무효화하기 위해 저장 */
    @Column(name = "last_review_updated_at")
    private OffsetDateTime lastReviewUpdatedAt;

    public void update(
            String summary, Integer reviewCountAtSummary, OffsetDateTime lastReviewUpdatedAt, OffsetDateTime generatedAt) {
        this.summary = summary;
        this.reviewCountAtSummary = reviewCountAtSummary;
        this.lastReviewUpdatedAt = lastReviewUpdatedAt;
        this.generatedAt = generatedAt;
    }

    public boolean isFreshFor(long commentedCount, OffsetDateTime latestReviewUpdatedAt) {
        return this.reviewCountAtSummary == commentedCount
            && Objects.equals(this.lastReviewUpdatedAt, latestReviewUpdatedAt);
    }
}
