package com.min.edu.payment.dto.response;

import com.min.edu.admission.domain.ExchangeCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IssuedExchangeCodeResponse {

    private Long exchangeCodeId;
    private String code;

    public static IssuedExchangeCodeResponse from(ExchangeCode exchangeCode) {
        return new IssuedExchangeCodeResponse(
            exchangeCode.getId(),
            exchangeCode.getCode()
        );
    }
}
