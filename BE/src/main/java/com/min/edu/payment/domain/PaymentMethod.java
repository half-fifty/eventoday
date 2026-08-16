package com.min.edu.payment.domain;

public enum PaymentMethod {
    CARD("CARD"),
    VIRTUAL_ACCOUNT("VIRTUAL_ACCOUNT");

    private final String code;

    PaymentMethod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean matchesTossMethod(String tossMethod) {
        if (tossMethod == null || tossMethod.isBlank()) {
            return false;
        }

        String normalized = tossMethod.trim();
        if (code.equals(normalized)) {
            return true;
        }

        return switch (this) {
            case CARD -> "\uCE74\uB4DC".equals(normalized)
                || "EASY_PAY".equals(normalized)
                || "\uAC04\uD3B8\uACB0\uC81C".equals(normalized);
            case VIRTUAL_ACCOUNT -> "\uAC00\uC0C1\uACC4\uC88C".equals(normalized);
        };
    }
}
