package com.min.edu.payment.toss;

import com.min.edu.common.exception.GlobalErrorCode;

import lombok.Getter;

@Getter
public class TossPaymentClientException extends RuntimeException {

    private final GlobalErrorCode errorCode;
    private final String tossErrorCode;

    public TossPaymentClientException(GlobalErrorCode errorCode) {
        this(errorCode, null);
    }

    public TossPaymentClientException(GlobalErrorCode errorCode, String tossErrorCode) {
        this.errorCode = errorCode;
        this.tossErrorCode = tossErrorCode;
    }
}
