package com.min.edu.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final GlobalErrorCode errorCode;  // ← 되돌리기

    public BusinessException(GlobalErrorCode errorCode) {  // ← 되돌리기
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(GlobalErrorCode errorCode, Throwable cause) {  // ← 되돌리기
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}