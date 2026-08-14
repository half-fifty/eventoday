package com.min.edu.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class TossBankCodesTest {

    @Test
    void officialTossBankCodes_areAllSupportedWithoutDuplicates() {
        List<String> officialBankCodes = List.of(
            "39",
            "34",
            "12",
            "32",
            "45",
            "64",
            "88",
            "48",
            "27",
            "20",
            "71",
            "50",
            "37",
            "35",
            "90",
            "89",
            "92",
            "81",
            "54",
            "60",
            "03",
            "06",
            "31",
            "02",
            "11",
            "23",
            "07",
            "30"
        );

        assertThat(TossBankCodes.BANK_CODES).containsExactlyElementsOf(officialBankCodes);
        assertThat(new HashSet<>(TossBankCodes.BANK_CODES))
            .hasSize(TossBankCodes.BANK_CODES.size());
        assertThat(TossBankCodes.BANK_CODES).hasSize(28);
    }

    @Test
    void unsupportedBankValues_areRejected() {
        assertThat(TossBankCodes.isSupportedBankCode("KB 국민")).isFalse();
        assertThat(TossBankCodes.isSupportedBankCode("999")).isFalse();
        assertThat(TossBankCodes.isSupportedBankCode("")).isFalse();
        assertThat(TossBankCodes.isSupportedBankCode(null)).isFalse();
    }
}
