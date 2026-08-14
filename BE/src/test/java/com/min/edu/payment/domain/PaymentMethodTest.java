package com.min.edu.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentMethodTest {

    @Test
    void cardMatchesTossKoreanMethod() {
        assertThat(PaymentMethod.CARD.matchesTossMethod("카드")).isTrue();
    }

    @Test
    void virtualAccountMatchesTossKoreanMethod() {
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("가상계좌")).isTrue();
    }

    @Test
    void methodDoesNotMatchDifferentTossMethod() {
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("카드")).isFalse();
    }
}
