package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MobileGuideBoothDetailResponse {

    private Long boothId;
    private String boothCode;
    private String displayName;
    private String shortIntro;
    private String description;        // 부스 설명
    private String boothType;          // 부스 타입 (category 대신)
    private String location;           // 부스 위치
    private Double averageRating;      // 평균 별점
    private Long reviewCount;          // 리뷰 개수
    private Boolean isInterested;      // 관심 여부
    private Boolean hasAvailableSlots; // 예약 가능 여부
}