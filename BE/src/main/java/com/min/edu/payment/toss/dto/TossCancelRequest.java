package com.min.edu.payment.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TossCancelRequest(
        @JsonIgnore
        String paymentKey,
        String cancelReason,
        long cancelAmount,
        RefundReceiveAccount refundReceiveAccount
) {

    public TossCancelRequest(
            String paymentKey,
            String cancelReason,
            long cancelAmount) {
        this(paymentKey, cancelReason, cancelAmount, null);
    }

    public record RefundReceiveAccount(
            String bank,
            String accountNumber,
            String holderName
    ) {
    }
}
