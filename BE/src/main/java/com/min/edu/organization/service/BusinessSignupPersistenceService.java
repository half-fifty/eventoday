package com.min.edu.organization.service;

import java.time.OffsetDateTime;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessSignupPersistenceService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public BusinessSignupResponseDto save(
            BusinessSignupRequestDto request,
            String email,
            String passwordHash) {
        validateNotRegistered(request.getBusinessNumber(), email);

        OffsetDateTime now = OffsetDateTime.now();

        try {
            Member member = memberRepository.save(
                Member.createBusinessMember(
                    email,
                    request.getOrganizationName().trim(),
                    passwordHash,
                    now
                )
            );

            Organization organization = organizationRepository.save(
                Organization.createBusinessOrganization(
                    request.getOrganizationType(),
                    request.getOrganizationName().trim(),
                    request.getBusinessNumber(),
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
}
