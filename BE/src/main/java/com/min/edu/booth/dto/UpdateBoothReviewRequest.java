package com.min.edu.booth.dto;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.*;
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
    @Size(max = 300, message = "리뷰 내용은 300자 이하여야 합니다")
    private String content;  // ✅ getComment()는 content로 변경

    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다")
    private short rating;  // ✅ Integer 타입 (Short 아님)
}