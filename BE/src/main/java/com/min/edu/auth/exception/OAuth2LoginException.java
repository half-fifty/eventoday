package com.min.edu.auth.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.min.edu.common.exception.GlobalErrorCode;

import lombok.Getter;

@Getter
public class OAuth2LoginException extends OAuth2AuthenticationException {

    private final GlobalErrorCode errorCode;

    public OAuth2LoginException(GlobalErrorCode errorCode) {
        super(
            new OAuth2Error(
                errorCode.getCode(),
                errorCode.getMessage(),
                null
            ),
            errorCode.getMessage()
        );
        this.errorCode = errorCode;
    }
}
