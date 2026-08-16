package com.min.edu.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentMethodTest {

    @Test
    void cardMatchesTossKoreanMethod() {
        assertThat(PaymentMethod.CARD.matchesTossMethod("카드")).isTrue();
    }

    @Test
    void cardMatchesTossEasyPayMethod() {
        assertThat(PaymentMethod.CARD.matchesTossMethod("EASY_PAY")).isTrue();
        assertThat(PaymentMethod.CARD.matchesTossMethod("\uAC04\uD3B8\uACB0\uC81C")).isTrue();
    }

    @Test
    void virtualAccountMatchesTossKoreanMethod() {
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("가상계좌")).isTrue();
    }

    @Test
    void methodDoesNotMatchDifferentTossMethod() {
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("카드")).isFalse();
    }

    @Test
    void virtualAccountDoesNotMatchTossEasyPayMethod() {
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("EASY_PAY")).isFalse();
        assertThat(PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod("\uAC04\uD3B8\uACB0\uC81C")).isFalse();
    }
}
