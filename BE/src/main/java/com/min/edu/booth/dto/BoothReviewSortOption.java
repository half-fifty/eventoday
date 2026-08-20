package com.min.edu.booth.dto;

// 문자열 정렬 필드를 그대로 받으면 임의 컬럼 정렬 등 오남용 여지가 있어, 허용할 정렬 기준을
// enum으로 고정해서 받는다.
public enum BoothReviewSortOption {
    LATEST,
    RATING_DESC,
    RATING_ASC
}
