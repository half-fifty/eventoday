package com.min.edu.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PaymentOrderAdvertisementMethodTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-23T12:00:00+09:00");

    @Test
    void 광고_결제대기_주문은_가상계좌를_선택할_수_있다() {
        PaymentOrder order = pendingAdvertisementOrder();

        order.selectPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT, now.plusMinutes(1));

        assertEquals(PaymentMethod.VIRTUAL_ACCOUNT, order.getRequestedPaymentMethod());
    }

    @Test
    void 입금대기_전환_후에는_결제수단을_변경할_수_없다() {
        PaymentOrder order = pendingAdvertisementOrder();
        order.selectPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT, now);
        order.markWaitingForDeposit(now.plusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> order.selectPaymentMethod(PaymentMethod.CARD, now.plusMinutes(2)));
    }

    @Test
    void 결제수단이_비어있으면_선택할_수_없다() {
        PaymentOrder order = pendingAdvertisementOrder();

        assertThrows(IllegalArgumentException.class,
                () -> order.selectPaymentMethod(null, now));
    }

    @Test
    void 토스가_발급한_가상계좌_만료시각으로_주문을_정렬한다() {
        PaymentOrder order = pendingAdvertisementOrder();
        OffsetDateTime tossDueAt = now.plusHours(24);

        order.selectPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT, now);
        order.alignVirtualAccountExpiry(tossDueAt, now.plusSeconds(1));

        assertEquals(tossDueAt, order.getExpiresAt());
    }

    private PaymentOrder pendingAdvertisementOrder() {
        return PaymentOrder.createEventAdOrder("AD-ORDER-1", 1L, "광고주", "ad@example.com",
                BigDecimal.valueOf(100_000), now.plusMinutes(30), now);
    }
}
