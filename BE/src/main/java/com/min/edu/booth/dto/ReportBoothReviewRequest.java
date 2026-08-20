package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothReviewReportReason;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "신고 사유를 선택해주세요")
    private BoothReviewReportReason reasonCode;

    // reasonCode == OTHER일 때 부가 설명으로 쓰는 자유 텍스트 (선택)
    @Size(max = 200, message = "상세 사유는 200자 이하여야 합니다")
    private String reason;
}
