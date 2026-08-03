package com.min.edu.organization.service;

import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.dto.BusinessSignupRequestDto;
import com.min.edu.organization.dto.BusinessSignupResponseDto;
import com.min.edu.organization.dto.BusinessVerificationResponseDto;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessSignupService {

    private final BusinessVerificationService businessVerificationService;
    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public BusinessSignupResponseDto signup(BusinessSignupRequestDto request) {
        String businessNumber = request.getBusinessNumber();
        String email = request.getContactEmail().trim().toLowerCase(Locale.ROOT);

        validateNotRegistered(businessNumber, email);
        validateBusiness(request);

        OffsetDateTime now = OffsetDateTime.now();

        try {
            Member member = memberRepository.save(
                Member.createBusinessMember(
                    email,
                    request.getOrganizationName().trim(),
                    passwordEncoder.encode(request.getPassword()),
                    now
                )
            );

            Organization organization = organizationRepository.save(
                Organization.createBusinessOrganization(
                    request.getOrganizationType(),
                    request.getOrganizationName().trim(),
                    businessNumber,
                    request.getRepresentativeName().trim(),
                    email,
                    request.getContactPhone().trim(),
                    now
                )
            );

            organizationMemberRepository.saveAndFlush(
                OrganizationMember.createOwner(
                    organization.getId(),
                    member.getId(),
                    now
                )
            );

            return new BusinessSignupResponseDto(
                member.getId(),
                organization.getId()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                GlobalErrorCode.BUSINESS_SIGNUP_CONFLICT
            );
        }
    }

    private void validateNotRegistered(String businessNumber, String email) {
        if (organizationRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(
                GlobalErrorCode.BUSINESS_NUMBER_ALREADY_REGISTERED
            );
        }

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(
                GlobalErrorCode.MEMBER_EMAIL_ALREADY_REGISTERED
            );
        }
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
