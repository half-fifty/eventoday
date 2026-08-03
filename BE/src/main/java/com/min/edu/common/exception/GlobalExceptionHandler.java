package com.min.edu.common.exception;

import com.min.edu.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        GlobalErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        GlobalErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE;
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getDefaultMessage())
            .orElse(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode, message));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException e) {
        GlobalErrorCode errorCode = GlobalErrorCode.RECRUITMENT_CONCURRENT_MODIFICATION;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException e) {
        GlobalErrorCode errorCode = GlobalErrorCode.ENTITY_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(
            NoHandlerFoundException e) {
        GlobalErrorCode errorCode = GlobalErrorCode.ENTITY_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리하지 못한 예외가 발생했습니다.", e);
        GlobalErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }
}
