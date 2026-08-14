package com.min.edu.payment.toss;

import java.util.List;
import java.util.Set;

public final class TossBankCodes {

    public static final List<String> BANK_CODES = List.of(
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

    private static final Set<String> BANK_CODE_SET = Set.copyOf(BANK_CODES);

    private TossBankCodes() {
    }

    public static boolean isSupportedBankCode(String bankCode) {
        return bankCode != null && BANK_CODE_SET.contains(bankCode);
    }
}
