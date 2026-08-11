package com.min.edu.application.service;

/**
 * 부스 신청 승인/반려 결정 이벤트 (EventReviewDecision과 동일한 패턴)
 * - 트랜잭션 커밋 이후 알림·메일 발송을 위해 ApplicationEventPublisher로 발행한다
 */
public record BoothApplicationDecision(
        Long applicationId,
        String applicationNo,
        Long applicantMemberId,
        String contactEmail,
        boolean approved,
        String rejectionReason) {}
