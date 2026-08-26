package com.min.edu.admission.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

@Component
public class ExchangeCodeRequestExceptionTranslator {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    static final String REQUESTED_EVENT_UNIQUE_CONSTRAINT =
        "uk_exchange_code_requests_event_requested";

    public BusinessException translate(RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException dataIntegrityViolationException
                && isRequestedEventUniqueViolation(dataIntegrityViolationException)) {
            return new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_EXISTS,
                exception
            );
        }

        return null;
    }

    private boolean isRequestedEventUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return REQUESTED_EVENT_UNIQUE_CONSTRAINT.equals(
                    constraintViolationException.getConstraintName()
                ) && UNIQUE_VIOLATION_SQL_STATE.equals(
                    constraintViolationException.getSQLState()
                );
            }

            current = current.getCause();
        }

        return false;
    }
}
