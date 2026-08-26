package com.min.edu.booth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateBoothReviewRequest {

    @NotNull(message = "별점은 필수입니다")
    @Min(value = 1, message = "별점은 1 이상이어야 합니다")
    @Max(value = 5, message = "별점은 5 이하여야 합니다")
    private Short rating;

    private String comment;  // 리뷰 텍스트 (선택사항)

    // POST /v1/files로 먼저 업로드해서 받은 fileId 목록 (선택사항, 최대 5장)
    @Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다")
    private List<Long> fileIds;
}