package com.min.edu.payment.toss;

import com.min.edu.common.exception.GlobalErrorCode;

import lombok.Getter;

@Getter
public class TossPaymentClientException extends RuntimeException {

    private final GlobalErrorCode errorCode;

    public TossPaymentClientException(GlobalErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
