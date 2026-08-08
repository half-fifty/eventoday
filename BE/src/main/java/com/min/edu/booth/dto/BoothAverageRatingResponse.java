package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothAverageRatingResponse {

    private Long boothId;
    private Double averageRating;  // 평균 별점 (null이면 리뷰 없음)
    private Long reviewCount;       // 리뷰 개수
}
