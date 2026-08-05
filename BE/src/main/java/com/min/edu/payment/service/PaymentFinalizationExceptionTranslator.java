package com.min.edu.payment.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

@Component
public class PaymentFinalizationExceptionTranslator {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    static final String PAYMENT_KEY_UNIQUE_CONSTRAINT = "payments_payment_key_key";

    public BusinessException translate(RuntimeException exception) {
        if (exception instanceof PessimisticLockingFailureException) {
            return new BusinessException(GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT);
        }

        if (exception instanceof DataIntegrityViolationException dataIntegrityViolationException
                && isPaymentKeyUniqueViolation(dataIntegrityViolationException)) {
            return new BusinessException(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
        }

        return null;
    }

    private boolean isPaymentKeyUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return PAYMENT_KEY_UNIQUE_CONSTRAINT.equals(
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
