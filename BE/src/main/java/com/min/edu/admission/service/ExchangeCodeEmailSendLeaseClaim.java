package com.min.edu.admission.service;

public record ExchangeCodeEmailSendLeaseClaim(
        ExchangeCodeEmailSendLeaseResult result,
        String token
) {

    public static ExchangeCodeEmailSendLeaseClaim acquired(String token) {
        return new ExchangeCodeEmailSendLeaseClaim(
            ExchangeCodeEmailSendLeaseResult.ACQUIRED,
            token
        );
    }

    public static ExchangeCodeEmailSendLeaseClaim alreadyInFlight() {
        return new ExchangeCodeEmailSendLeaseClaim(
            ExchangeCodeEmailSendLeaseResult.ALREADY_IN_FLIGHT,
            null
        );
    }

    public static ExchangeCodeEmailSendLeaseClaim unavailable() {
        return new ExchangeCodeEmailSendLeaseClaim(
            ExchangeCodeEmailSendLeaseResult.UNAVAILABLE,
            null
        );
    }

    public boolean acquired() {
        return result == ExchangeCodeEmailSendLeaseResult.ACQUIRED;
    }
}
