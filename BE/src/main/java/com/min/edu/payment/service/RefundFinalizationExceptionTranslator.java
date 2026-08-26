package com.min.edu.payment.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

@Component
public class RefundFinalizationExceptionTranslator {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    static final String PAYMENT_REFUND_PAYMENT_UNIQUE_CONSTRAINT =
        "uk_payment_refunds_payment";

    public BusinessException translate(RuntimeException exception) {
        if (exception instanceof PessimisticLockingFailureException) {
            return new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
        }

        if (exception instanceof DataIntegrityViolationException dataIntegrityViolationException
                && isPaymentRefundUniqueViolation(dataIntegrityViolationException)) {
            return new BusinessException(GlobalErrorCode.REFUND_ALREADY_COMPLETED);
        }

        return null;
    }

    private boolean isPaymentRefundUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return PAYMENT_REFUND_PAYMENT_UNIQUE_CONSTRAINT.equals(
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
