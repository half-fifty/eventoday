package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// review id 범위 [fromReviewId, toReviewId]로 고정된 "닫힌 배치"의 요약 캐시. 배치를 개수/위치가 아니라
// id 범위로 고정해두는 이유: 중간 리뷰가 삭제돼도 이후 리뷰들의 배치 소속이 밀리지 않는다(위치 기반이었다면
// 삭제 하나로 그 뒤 모든 배치 경계가 재계산돼야 했을 것). reviewCount/lastReviewUpdatedAt은 이 범위 안의
// 리뷰가 삭제되거나 수정됐는지 감지해 해당 배치만 다시 요약하기 위한 신선도 체크용이다.
@Entity
@Table(
        name = "booth_review_summary_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_review_summary_batches",
                columnNames = {"booth_id", "batch_index"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReviewSummaryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "batch_index", nullable = false)
    private Integer batchIndex;

    @Column(name = "from_review_id", nullable = false)
    private Long fromReviewId;

    @Column(name = "to_review_id", nullable = false)
    private Long toReviewId;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "last_review_updated_at")
    private OffsetDateTime lastReviewUpdatedAt;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;
}
