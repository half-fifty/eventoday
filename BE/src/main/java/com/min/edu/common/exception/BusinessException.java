package com.min.edu.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final GlobalErrorCode errorCode;

    public BusinessException(GlobalErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(GlobalErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
