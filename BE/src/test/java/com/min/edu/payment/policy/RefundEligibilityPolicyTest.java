package com.min.edu.payment.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.payment.policy.RefundEligibilityPolicy.RefundEligibilityInput;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RefundEligibilityPolicyTest {

    private final RefundEligibilityPolicy policy =
        new RefundEligibilityPolicy(new EventOperationDeadlinePolicy());

    @Test
    void returnsPaymentNotPaidWhenPaymentStatusIsNotPaid() {
        RefundEligibilityResult result = evaluate("WAITING_FOR_DEPOSIT", "PAID", "CONFIRMED",
            BigDecimal.valueOf(10000), false, now(), now().plusHours(3));

        assertThat(result.refundable()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.PAYMENT_NOT_PAID);
    }

    @Test
    void returnsPaymentOrderNotPaidWhenPaymentOrderStatusIsNotPaid() {
        RefundEligibilityResult result = evaluate("PAID", "WAITING_FOR_DEPOSIT", "CONFIRMED",
            BigDecimal.valueOf(10000), false, now(), now().plusHours(3));

        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.PAYMENT_ORDER_NOT_PAID);
    }

    @Test
    void returnsTicketOrderNotConfirmedWhenTicketOrderIsNotConfirmed() {
        RefundEligibilityResult result = evaluate("PAID", "PAID", "PENDING_PAYMENT",
            BigDecimal.valueOf(10000), false, now(), now().plusHours(3));

        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.TICKET_ORDER_NOT_CONFIRMED);
    }

    @Test
    void returnsRefundAmountNotPositiveWhenAmountIsZeroOrNegative() {
        RefundEligibilityResult zero = evaluate("PAID", "PAID", "CONFIRMED",
            BigDecimal.ZERO, false, now(), now().plusHours(3));
        RefundEligibilityResult negative = evaluate("PAID", "PAID", "CONFIRMED",
            BigDecimal.valueOf(-1), false, now(), now().plusHours(3));

        assertThat(zero.reasonCode()).isEqualTo(RefundEligibilityReasonCode.REFUND_AMOUNT_NOT_POSITIVE);
        assertThat(negative.reasonCode()).isEqualTo(RefundEligibilityReasonCode.REFUND_AMOUNT_NOT_POSITIVE);
    }

    @Test
    void returnsOperationCutoffPassedAtOrAfterCutoff() {
        OffsetDateTime now = now();

        RefundEligibilityResult result = evaluate("PAID", "PAID", "CONFIRMED",
            BigDecimal.valueOf(10000), false, now, now.plusHours(1));

        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.OPERATION_CUTOFF_PASSED);
        assertThat(result.operationCutoffAt()).isEqualTo(now);
    }

    @Test
    void returnsExchangeCodeAlreadyRedeemedWhenRedeemedExists() {
        RefundEligibilityResult result = evaluate("PAID", "PAID", "CONFIRMED",
            BigDecimal.valueOf(10000), true, now(), now().plusHours(3));

        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED);
    }

    @Test
    void returnsEligibleForCurrentRefundableState() {
        OffsetDateTime now = now();

        RefundEligibilityResult result = evaluate("PAID", "PAID", "CONFIRMED",
            BigDecimal.valueOf(10000), false, now, now.plusHours(3));

        assertThat(result.refundable()).isTrue();
        assertThat(result.reasonCode()).isEqualTo(RefundEligibilityReasonCode.ELIGIBLE);
        assertThat(result.operationCutoffAt()).isEqualTo(now.plusHours(2));
    }

    private RefundEligibilityResult evaluate(
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            BigDecimal amount,
            boolean exchangeCodeRedeemed,
            OffsetDateTime evaluatedAt,
            OffsetDateTime eventEndAt) {
        return policy.evaluate(new RefundEligibilityInput(
            paymentStatus,
            paymentOrderStatus,
            ticketOrderStatus,
            amount,
            eventEndAt,
            exchangeCodeRedeemed,
            evaluatedAt
        ));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
    }
}
