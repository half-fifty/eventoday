package com.min.edu.payment.domain;

public enum PaymentAuditActorType {
    MEMBER,
    GUEST,
    SYSTEM;

    public static PaymentAuditActorType fromRequester(Long requesterMemberId) {
        return requesterMemberId == null ? GUEST : MEMBER;
    }
}
