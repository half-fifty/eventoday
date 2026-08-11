package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewResponse {

    private Long id;
    private Long boothId;
    private String memberName;      // 사용자 닉네임/이름
    private String memberProfile;   // 프로필 사진 URL (선택)
    private Short rating;
    private String comment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;  // ⭐ 추가!

    public static BoothReviewResponse from(BoothReview review) {
        return BoothReviewResponse.builder()
                .id(review.getId())
                .boothId(review.getBoothId())
                .memberName(review.getMemberName())
                .memberProfile(review.getMemberProfile())  // ⭐ 추가 (빠진 것)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())  // ⭐ 추가!
                .build();
    }
}