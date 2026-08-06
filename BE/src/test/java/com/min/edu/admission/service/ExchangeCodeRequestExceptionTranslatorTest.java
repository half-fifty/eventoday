package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ExchangeCodeRequestExceptionTranslatorTest {

    private final ExchangeCodeRequestExceptionTranslator translator =
        new ExchangeCodeRequestExceptionTranslator();

    @Test
    void translate_returnsDuplicateRequestErrorForRequestedEventUniqueConstraint() {
        BusinessException result = translator.translate(dataIntegrityViolation(
            ExchangeCodeRequestExceptionTranslator.REQUESTED_EVENT_UNIQUE_CONSTRAINT,
            "23505"
        ));

        assertThat(result).isNotNull();
        assertThat(result.getErrorCode())
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_EXISTS);
    }

    @Test
    void translate_returnsNullForOtherConstraintName() {
        BusinessException result = translator.translate(dataIntegrityViolation(
            "fk_exchange_code_requests_requested_by",
            "23503"
        ));

        assertThat(result).isNull();
    }

    private DataIntegrityViolationException dataIntegrityViolation(
            String constraintName,
            String sqlState) {
        ConstraintViolationException cause = new ConstraintViolationException(
            "constraint violation",
            new SQLException("constraint violation", sqlState),
            constraintName
        );
        return new DataIntegrityViolationException("data integrity violation", cause);
    }
}
