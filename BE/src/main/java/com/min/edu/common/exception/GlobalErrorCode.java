package com.min.edu.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_400_001", "지원하지 않는 OAuth 제공자입니다."),
    OAUTH_REQUIRED_ATTRIBUTE_MISSING(HttpStatus.BAD_REQUEST, "AUTH_400_002", "소셜 로그인 사용자 정보가 부족합니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_001", "Refresh Token이 필요합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_002", "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_401_003", "로그인 정보가 만료되었습니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_401_010", "소셜 로그인에 실패했습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    MEMBER_LOGIN_RESTRICTED(HttpStatus.FORBIDDEN, "AUTH_403_001", "로그인이 제한된 회원입니다."),
    OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH_403_002", "검증된 이메일로만 소셜 로그인을 이용할 수 있습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
