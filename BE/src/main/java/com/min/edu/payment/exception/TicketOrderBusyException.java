package com.min.edu.payment.exception;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import lombok.Getter;

@Getter
public class TicketOrderBusyException extends BusinessException {

    private final long retryAfterSeconds;

    public TicketOrderBusyException(long retryAfterSeconds) {
        super(GlobalErrorCode.TICKET_ORDER_BUSY);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
