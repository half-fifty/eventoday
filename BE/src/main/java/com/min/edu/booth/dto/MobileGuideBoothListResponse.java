package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MobileGuideBoothListResponse {

    private Long boothId;
    private String boothCode;
    private String displayName;
    private String shortIntro;
    private Double averageRating;  // 평균 별점
    private Long reviewCount;       // 리뷰 개수
    private Boolean isInterested;   // 관심 여부 (로그인 시)
}