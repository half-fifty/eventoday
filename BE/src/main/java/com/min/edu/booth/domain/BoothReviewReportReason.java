package com.min.edu.booth.domain;

// 리뷰 신고 사유. 자유 텍스트만 받으면 나중에 집계·우선순위 판단이 어려워 정해진 코드로 받는다.
public enum BoothReviewReportReason {
    SPAM,               // 광고/도배
    ABUSE,              // 욕설/혐오 표현
    HARASSMENT,         // 특정인 비방/괴롭힘
    FALSE_INFORMATION,  // 허위 정보
    OTHER               // 기타 (reason 텍스트에 상세 사유 기재)
}
