package com.min.edu.payment.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TossCancelRequest(
        @JsonIgnore
        String paymentKey,
        String cancelReason,
        long cancelAmount
) {
}
