package com.min.edu.booth.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportBoothReviewRequest {

    @Size(max = 200, message = "신고 사유는 200자 이하여야 합니다")
    private String reason;  // 선택 사항
}
