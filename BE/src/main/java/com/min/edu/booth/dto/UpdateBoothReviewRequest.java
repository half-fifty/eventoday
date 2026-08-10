package com.min.edu.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBoothReviewRequest {

    @NotBlank(message = "리뷰 내용은 필수입니다")
    private String content;  // ✅ getComment()는 content로 변경

    @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다")
    private Integer rating;  // ✅ Integer 타입 (Short 아님)
}