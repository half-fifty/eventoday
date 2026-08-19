package com.min.edu.ai.dto;

import com.min.edu.payment.policy.RefundEligibilityResult;
import com.min.edu.payment.service.RefundEligibilityView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundFailureExplanationContext(
        boolean refundable,
        String reasonCode,
        String fallbackMessage,
        String paymentStatus,
        String paymentOrderStatus,
        String ticketOrderStatus,
        String refundStatus,
        String paymentMethod,
        BigDecimal refundAmount,
        String eventName,
        OffsetDateTime eventEndAt,
        OffsetDateTime operationCutoffAt,
        boolean exchangeCodeRedeemed,
        OffsetDateTime evaluatedAt) {

    public static RefundFailureExplanationContext from(
            RefundEligibilityView view,
            String fallbackMessage) {
        RefundEligibilityResult eligibility = view.eligibility();
        return new RefundFailureExplanationContext(
            eligibility.refundable(),
            eligibility.reasonCode().name(),
            fallbackMessage,
            eligibility.paymentStatus(),
            eligibility.paymentOrderStatus(),
            eligibility.ticketOrderStatus(),
            view.refundStatus(),
            view.paymentMethod(),
            view.refundAmount(),
            view.eventName(),
            eligibility.eventEndAt(),
            eligibility.operationCutoffAt(),
            eligibility.exchangeCodeRedeemed(),
            view.evaluatedAt()
        );
    }
}
