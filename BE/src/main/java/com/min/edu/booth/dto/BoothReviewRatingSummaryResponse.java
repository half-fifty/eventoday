package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewRatingSummaryResponse {

    private Double averageRating;

    private long reviewCount;  // 숨김 처리된 리뷰는 제외한 공개 리뷰 개수
}
