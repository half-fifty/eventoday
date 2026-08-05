package com.min.edu.booth.exception;

import com.min.edu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BoothErrorCode implements ErrorCode {

    RESERVATION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "BOOTH_400_001", "이미 이 부스를 예약했습니다"),
    SLOT_FULL(HttpStatus.BAD_REQUEST, "BOOTH_400_002", "정원이 가득 찼습니다"),
    SLOT_CLOSED(HttpStatus.BAD_REQUEST, "BOOTH_400_003", "마감된 시간대입니다"),
    CANNOT_CANCEL_RESERVATION(HttpStatus.BAD_REQUEST, "BOOTH_400_004", "이미 진행된 예약은 취소할 수 없습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}