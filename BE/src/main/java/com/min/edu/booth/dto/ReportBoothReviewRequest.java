package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothReviewReportReason;
import jakarta.validation.constraints.AssertTrue;
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

    // reasonCode == OTHER일 때 부가 설명으로 쓰는 자유 텍스트 (그 외 사유는 선택)
    @Size(max = 200, message = "상세 사유는 200자 이하여야 합니다")
    private String reason;

    // "기타" 사유인데 상세 설명이 없으면 운영자가 무엇을 신고했는지 알 수 없으니 이 경우만 필수로 강제한다.
    @AssertTrue(message = "기타 사유를 선택한 경우 상세 사유를 입력해주세요")
    public boolean isReasonValid() {
        return reasonCode != BoothReviewReportReason.OTHER || (reason != null && !reason.isBlank());
    }
}
