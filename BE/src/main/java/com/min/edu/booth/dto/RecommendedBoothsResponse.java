package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedBoothsResponse {

    private List<RecommendedBooth> recommendedBooths;  // 추천 부스들
    private List<CongestionBooth> congestedBooths;     // 혼잡한 부스들
    private String recommendation;                       // 추천 메시지

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendedBooth {
        private Long boothId;
        private Long congestionCount;  // 현재 혼잡도
        private Integer rank;          // 추천 순위
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CongestionBooth {
        private Long boothId;
        private Long congestionCount;  // 혼잡도
    }
}