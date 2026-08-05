package com.min.edu.booth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BoothErrorCode {
    // 예약 관련
    RESERVATION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "BOOTH_400_001", "이미 이 부스를 예약했습니다"),
    SLOT_FULL(HttpStatus.BAD_REQUEST, "BOOTH_400_002", "정원이 가득 찼습니다"),
    SLOT_CLOSED(HttpStatus.BAD_REQUEST, "BOOTH_400_003", "마감된 시간대입니다"),
    CANNOT_CANCEL_RESERVATION(HttpStatus.BAD_REQUEST, "BOOTH_400_004", "이미 진행된 예약은 취소할 수 없습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;

    BoothErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
