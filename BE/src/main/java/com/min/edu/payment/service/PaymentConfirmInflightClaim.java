package com.min.edu.payment.service;

public record PaymentConfirmInflightClaim(
        InflightClaimResult result,
        String token
) {

    public static PaymentConfirmInflightClaim acquired(String token) {
        return new PaymentConfirmInflightClaim(InflightClaimResult.ACQUIRED, token);
    }

    public static PaymentConfirmInflightClaim alreadyInFlight() {
        return new PaymentConfirmInflightClaim(InflightClaimResult.ALREADY_IN_FLIGHT, null);
    }

    public static PaymentConfirmInflightClaim failOpen() {
        return new PaymentConfirmInflightClaim(InflightClaimResult.FAIL_OPEN, null);
    }

    public boolean acquired() {
        return result == InflightClaimResult.ACQUIRED;
    }
}
