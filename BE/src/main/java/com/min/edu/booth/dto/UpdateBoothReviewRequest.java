package com.min.edu.booth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
    private String content;

    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다")
    private Short rating;  // ✅ Short 타입 (nullable, @NotNull 검증)

    // null이면 기존 사진을 그대로 두고, 값이 오면(빈 배열 포함) 그 목록으로 통째로 교체한다.
    @Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다")
    private List<Long> fileIds;
}