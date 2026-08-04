package com.min.edu.payment.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank
    @Size(max = 200)
    private String paymentKey;

    @NotBlank
    @Size(min = 6, max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String orderId;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    public ConfirmPaymentRequest(
            String paymentKey,
            String orderId,
            BigDecimal amount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
    }
}
