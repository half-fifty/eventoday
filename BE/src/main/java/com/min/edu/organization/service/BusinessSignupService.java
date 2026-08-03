package com.min.edu.organization.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.dto.BusinessSignupRequestDto;
import com.min.edu.organization.dto.BusinessSignupResponseDto;
import com.min.edu.organization.dto.BusinessVerificationResponseDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessSignupService {

    private final BusinessVerificationService businessVerificationService;
    private final BusinessSignupPersistenceService persistenceService;
    private final PasswordEncoder passwordEncoder;

    public BusinessSignupResponseDto signup(BusinessSignupRequestDto request) {
        validateBusiness(request);

        String email = request.getContactEmail()
            .trim()
            .toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.getPassword());

        return persistenceService.save(request, email, passwordHash);
    }

    private void validateBusiness(BusinessSignupRequestDto request) {
        BusinessVerificationResponseDto verification =
            businessVerificationService.verify(
                request.getBusinessNumber(),
                request.getStartDate(),
                request.getRepresentativeName()
            );

        if (!verification.isValid()) {
            throw new BusinessException(
                GlobalErrorCode.BUSINESS_VERIFICATION_FAILED
            );
        }

        if (!verification.isActive()) {
            throw new BusinessException(
                GlobalErrorCode.BUSINESS_NOT_ACTIVE
            );
        }
    }
}
