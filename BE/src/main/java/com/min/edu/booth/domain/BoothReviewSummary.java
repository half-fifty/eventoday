package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
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

    public void update(String summary, Integer reviewCountAtSummary, OffsetDateTime generatedAt) {
        this.summary = summary;
        this.reviewCountAtSummary = reviewCountAtSummary;
        this.generatedAt = generatedAt;
    }
}
