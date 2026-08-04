package com.min.edu.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode {

    KAKAO_MAP_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_503_001", "카카오 장소 검색 서비스를 이용할 수 없습니다."),

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_400_001", "지원하지 않는 OAuth 제공자입니다."),
    OAUTH_REQUIRED_ATTRIBUTE_MISSING(HttpStatus.BAD_REQUEST, "AUTH_400_002", "소셜 로그인 사용자 정보가 부족합니다."),
    BUSINESS_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "ORGANIZATION_400_001", "사업자 정보가 일치하지 않습니다."),
    BUSINESS_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "ORGANIZATION_400_002", "현재 영업 중인 사업자만 가입할 수 있습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_001", "Refresh Token이 필요합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_002", "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_401_003", "로그인 정보가 만료되었습니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_401_010", "소셜 로그인에 실패했습니다."),
    INVALID_BUSINESS_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_011", "사업자등록번호 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    MEMBER_LOGIN_RESTRICTED(HttpStatus.FORBIDDEN, "AUTH_403_001", "로그인이 제한된 회원입니다."),
    OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH_403_002", "검증된 이메일로만 소셜 로그인을 이용할 수 있습니다."),
    ORDER_ACCESS_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "TICKET_401_001", "주문 접근 토큰이 필요합니다."),
    ORDER_ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TICKET_401_002", "주문 접근 토큰이 유효하지 않습니다."),
    ORDER_ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TICKET_401_003", "주문 접근 토큰이 만료되었습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TICKET_403_001", "주문 조회 권한이 없습니다."),
    BUSINESS_NUMBER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "ORGANIZATION_409_001", "이미 가입된 사업자등록번호입니다."),
    MEMBER_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "MEMBER_409_001", "이미 사용 중인 이메일입니다."),
    BUSINESS_SIGNUP_CONFLICT(HttpStatus.CONFLICT, "ORGANIZATION_409_002", "사업자 가입 정보가 이미 사용 중입니다."),
    NTS_BUSINESS_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ORGANIZATION_503_001", "사업자 정보 확인 서비스를 이용할 수 없습니다."),
    INVALID_GUEST_BUYER_INFO(HttpStatus.BAD_REQUEST, "TICKET_400_001", "비회원 구매자 정보가 올바르지 않습니다."),
    TICKET_PURCHASE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "TICKET_422_001", "티켓 구매 제한 수량을 초과했습니다."),
    TICKET_SALES_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "TICKET_422_002", "현재 티켓 판매 기간이 아닙니다."),
    TICKET_SOLD_OUT(HttpStatus.CONFLICT, "TICKET_409_001", "티켓 재고가 부족합니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_404_001", "행사를 찾을 수 없습니다."),
    TICKET_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "TICKET_404_002", "티켓 주문을 찾을 수 없습니다."),
    ORDER_NUMBER_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_500_001", "주문번호 생성에 실패했습니다."),
    EXCHANGE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ADMISSION_500_001", "교환 코드 생성에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "FILE_400_001", "허용되지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_400_002", "파일 크기가 허용 한도를 초과했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_404_001", "파일을 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FILE_403_001", "파일에 접근할 권한이 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_500_001", "파일 업로드에 실패했습니다."),
    RECRUITMENT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "RECRUITMENT_400_001", "모집 종료일은 시작일 이후여야 합니다."),
    RECRUITMENT_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "RECRUITMENT_400_002", "이미 모집 공고가 등록된 행사입니다."),
    RECRUITMENT_STATUS_TRANSITION_INVALID(HttpStatus.BAD_REQUEST, "RECRUITMENT_400_003", "허용되지 않는 상태 변경입니다."),
    RECRUITMENT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "RECRUITMENT_409_001", "다른 요청에 의해 이미 처리되었습니다. 새로고침 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
