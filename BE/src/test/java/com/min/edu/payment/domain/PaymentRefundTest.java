package com.min.edu.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PaymentRefundTest {

    @Test
    void PG_취소후_로컬처리가_실패하면_취소키를_보존한다() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-08-24T10:00:00+09:00");
        OffsetDateTime failedAt = requestedAt.plusMinutes(1);
        PaymentRefund refund = PaymentRefund.requested(
                1L, 2L, BigDecimal.valueOf(100_000), "광고 취소", requestedAt);

        refund.failAfterGatewayCancellation("cancel-key-1", failedAt);

        assertEquals(PaymentRefundStatus.FAILED, refund.getStatus());
        assertEquals("cancel-key-1", refund.getPgCancelKey());
        assertEquals(failedAt, refund.getCompletedAt());
    }
}
