package com.min.edu.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateRefundRequest {

    @NotBlank
    @Size(max = 200)
    private String reason;

    @Valid
    private RefundReceiveAccountRequest refundReceiveAccount;

    public CreateRefundRequest(String reason) {
        this.reason = reason;
    }

    public CreateRefundRequest(
            String reason,
            RefundReceiveAccountRequest refundReceiveAccount) {
        this.reason = reason;
        this.refundReceiveAccount = refundReceiveAccount;
    }

    @Getter
    @NoArgsConstructor
    public static class RefundReceiveAccountRequest {

        @NotBlank
        @Size(max = 20)
        private String bank;

        @NotBlank
        @Size(max = 40)
        private String accountNumber;

        @NotBlank
        @Size(max = 100)
        private String holderName;

        public RefundReceiveAccountRequest(
                String bank,
                String accountNumber,
                String holderName) {
            this.bank = bank;
            this.accountNumber = accountNumber;
            this.holderName = holderName;
        }
    }
}
