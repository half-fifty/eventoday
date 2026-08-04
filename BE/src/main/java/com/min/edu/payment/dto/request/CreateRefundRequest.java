package com.min.edu.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateRefundRequest {

    @NotBlank
    @Size(max = 200)
    private String reason;

    public CreateRefundRequest(String reason) {
        this.reason = reason;
    }
}
