package com.min.edu.payment.service;

import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;

public record TicketOrderIdempotencyClaimResult(
        TicketOrderIdempotencyClaimStatus status,
        TicketOrderIdempotencyRequest request
) {

    public static TicketOrderIdempotencyClaimResult claimed(TicketOrderIdempotencyRequest request) {
        return new TicketOrderIdempotencyClaimResult(TicketOrderIdempotencyClaimStatus.CLAIMED, request);
    }

    public static TicketOrderIdempotencyClaimResult completed(TicketOrderIdempotencyRequest request) {
        return new TicketOrderIdempotencyClaimResult(TicketOrderIdempotencyClaimStatus.COMPLETED, request);
    }

    public static TicketOrderIdempotencyClaimResult processing(TicketOrderIdempotencyRequest request) {
        return new TicketOrderIdempotencyClaimResult(TicketOrderIdempotencyClaimStatus.PROCESSING, request);
    }
}
