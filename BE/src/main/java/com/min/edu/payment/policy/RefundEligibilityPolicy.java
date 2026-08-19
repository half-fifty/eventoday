package com.min.edu.payment.policy;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class RefundEligibilityPolicy {

    private final EventOperationDeadlinePolicy deadlinePolicy;

    public RefundEligibilityPolicy(EventOperationDeadlinePolicy deadlinePolicy) {
        this.deadlinePolicy = deadlinePolicy;
    }

    public RefundEligibilityResult evaluate(RefundEligibilityInput input) {
        OffsetDateTime operationCutoffAt = deadlinePolicy.operationCutoff(input.eventEndAt());
        if (!PaymentStatus.PAID.name().equals(input.paymentStatus())) {
            return result(false, RefundEligibilityReasonCode.PAYMENT_NOT_PAID,
                input, operationCutoffAt);
        }
        if (!PaymentOrderStatus.PAID.name().equals(input.paymentOrderStatus())) {
            return result(false, RefundEligibilityReasonCode.PAYMENT_ORDER_NOT_PAID,
                input, operationCutoffAt);
        }
        if (!TicketOrderStatus.CONFIRMED.name().equals(input.ticketOrderStatus())) {
            return result(false, RefundEligibilityReasonCode.TICKET_ORDER_NOT_CONFIRMED,
                input, operationCutoffAt);
        }
        if (input.refundAmount() == null || input.refundAmount().signum() <= 0) {
            return result(false, RefundEligibilityReasonCode.REFUND_AMOUNT_NOT_POSITIVE,
                input, operationCutoffAt);
        }
        if (!deadlinePolicy.isBeforeOperationCutoff(input.evaluatedAt(), input.eventEndAt())) {
            return result(false, RefundEligibilityReasonCode.OPERATION_CUTOFF_PASSED,
                input, operationCutoffAt);
        }
        if (input.exchangeCodeRedeemed()) {
            return result(false, RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED,
                input, operationCutoffAt);
        }
        return result(true, RefundEligibilityReasonCode.ELIGIBLE, input, operationCutoffAt);
    }

    public void requireRefundable(RefundEligibilityResult result) {
        if (result.refundable()) {
            return;
        }
        if (result.reasonCode() == RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED) {
            throw new BusinessException(GlobalErrorCode.USED_TICKET_CANNOT_BE_REFUNDED);
        }
        throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
    }

    private RefundEligibilityResult result(
            boolean refundable,
            RefundEligibilityReasonCode reasonCode,
            RefundEligibilityInput input,
            OffsetDateTime operationCutoffAt) {
        return new RefundEligibilityResult(
            refundable,
            reasonCode,
            input.paymentStatus(),
            input.paymentOrderStatus(),
            input.ticketOrderStatus(),
            input.refundAmount(),
            input.eventEndAt(),
            operationCutoffAt,
            input.exchangeCodeRedeemed(),
            input.evaluatedAt()
        );
    }

    public record RefundEligibilityInput(
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            BigDecimal refundAmount,
            OffsetDateTime eventEndAt,
            boolean exchangeCodeRedeemed,
            OffsetDateTime evaluatedAt) {
    }
}
