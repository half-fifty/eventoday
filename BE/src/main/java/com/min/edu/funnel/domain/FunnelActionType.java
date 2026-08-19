package com.min.edu.funnel.domain;

/**
 * FE가 직접 발행할 수 있는 액션 타입만 포함한다.
 * complete_payment(결제완료)는 결제 도메인 Outbox가 발행하며 FE가 보내지 않으므로 여기 포함하지 않는다.
 */
public enum FunnelActionType {
    VIEW_EVENT_DETAIL,
    OPEN_PURCHASE_MODAL,
    VIEW_BOOTH_LIST,
    BACK_NAVIGATION,
    PAGE_CLOSE
}
