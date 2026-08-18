package com.min.edu.funnel.domain;

/** 핵심 구매 퍼널 4단계. VISIT은 별도 액션 없이 세션 존재 자체로 판정된다 (technical-design.md 참고). */
public enum FunnelStep {
    VISIT,
    VIEW_EVENT_DETAIL,
    OPEN_PURCHASE_MODAL,
    COMPLETE_PAYMENT
}
