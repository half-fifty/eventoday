package com.min.edu.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewReplyRequest {

    @NotBlank(message = "답글 내용은 필수입니다")
    @Size(max = 500, message = "답글은 500자 이하여야 합니다")
    private String content;
}
