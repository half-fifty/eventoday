package com.min.edu.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GuestOrderAccessTokenRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String phone;
}
