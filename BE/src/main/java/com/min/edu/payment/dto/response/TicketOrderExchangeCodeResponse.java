package com.min.edu.payment.dto.response;

import java.time.OffsetDateTime;

import com.min.edu.admission.domain.ExchangeCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketOrderExchangeCodeResponse {

    private Long exchangeCodeId;
    private String code;
    private String status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime redeemedAt;

    public static TicketOrderExchangeCodeResponse from(ExchangeCode exchangeCode) {
        return new TicketOrderExchangeCodeResponse(
            exchangeCode.getId(),
            exchangeCode.getCode(),
            exchangeCode.getStatus().name(),
            exchangeCode.getExpiresAt(),
            exchangeCode.getRedeemedAt()
        );
    }
}
