package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExchangeCodeRecipientEmailValidator {

    private static final Validator VALIDATOR =
        Validation.buildDefaultValidatorFactory().getValidator();

    private final ExchangeCodeRequestRepository exchangeCodeRequestRepository;
    private final MemberRepository memberRepository;

    public ExchangeCodeRecipientEmailValidator(
            ExchangeCodeRequestRepository exchangeCodeRequestRepository,
            MemberRepository memberRepository) {
        this.exchangeCodeRequestRepository = exchangeCodeRequestRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public void validateForRequest(Long requestId) {
        ExchangeCodeRequest request = exchangeCodeRequestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));
        Member recipient = memberRepository.findById(request.getRequestedBy())
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_NOT_FOUND
            ));

        validate(recipient.getEmail());
    }

    public static void validate(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_MISSING
            );
        }
        if (!VALIDATOR.validate(new EmailProbe(email)).isEmpty()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_INVALID
            );
        }
    }

    private record EmailProbe(@NotBlank @Email String email) {
    }
}
