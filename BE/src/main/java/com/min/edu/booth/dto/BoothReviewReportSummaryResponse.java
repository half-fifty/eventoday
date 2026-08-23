package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothReviewReportReason;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부스 담당자용 "신고된 리뷰" 대시보드 — 자동 숨김 임계치(3건)에 못 미친 신고 1~2건짜리 리뷰도
// 여기서 미리 확인하고 필요하면 hideReview로 선제 조치할 수 있게 한다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewReportSummaryResponse {

    private Long reviewId;

    private String memberName;

    private Short rating;

    private String comment;

    private boolean hidden;

    private long reportCount;

    private List<ReportDetail> reports;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportDetail {
        private BoothReviewReportReason reasonCode;
        private String reason;
        private OffsetDateTime createdAt;
    }
}
