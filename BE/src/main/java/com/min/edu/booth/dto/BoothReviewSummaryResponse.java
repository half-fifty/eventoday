package com.min.edu.booth.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewSummaryResponse {

    private Long boothId;

    /** 요약 생성에 필요한 최소 리뷰 수를 못 채웠으면 false (summary는 null) */
    private boolean available;

    private String summary;

    private int reviewCount;

    private Double ratingAverage;

    private OffsetDateTime generatedAt;

    /** OpenAI 재호출 실패로 최신화하지 못하고 이전 캐시를 반환한 경우 true */
    private boolean stale;
}
