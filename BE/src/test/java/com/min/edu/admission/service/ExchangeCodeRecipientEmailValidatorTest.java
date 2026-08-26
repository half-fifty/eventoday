package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

class ExchangeCodeRecipientEmailValidatorTest {

    @Test
    void validateAllowsWellFormedEmail() {
        assertThatNoException()
            .isThrownBy(() -> ExchangeCodeRecipientEmailValidator.validate("requester@example.com"));
    }

    @Test
    void validateRejectsNullEmail() {
        assertThatThrownBy(() -> ExchangeCodeRecipientEmailValidator.validate(null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_MISSING);
    }

    @Test
    void validateRejectsBlankEmail() {
        assertThatThrownBy(() -> ExchangeCodeRecipientEmailValidator.validate(" "))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_MISSING);
    }

    @Test
    void validateRejectsMalformedEmail() {
        assertThatThrownBy(() -> ExchangeCodeRecipientEmailValidator.validate("invalid-email"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_INVALID);
    }
}
