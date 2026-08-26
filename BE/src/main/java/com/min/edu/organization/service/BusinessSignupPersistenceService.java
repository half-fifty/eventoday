package com.min.edu.organization.service;

import java.time.OffsetDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.BusinessMemberProfile;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationSignupReview;
import com.min.edu.organization.domain.OrganizationType;
import com.min.edu.organization.dto.BusinessSignupRequestDto;
import com.min.edu.organization.dto.BusinessSignupResponseDto;
import com.min.edu.organization.repository.BusinessMemberProfileRepository;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;
import com.min.edu.organization.repository.OrganizationSignupReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessSignupPersistenceService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final BusinessMemberProfileRepository businessMemberProfileRepository;
    private final OrganizationSignupReviewRepository organizationSignupReviewRepository;
    private final FileService fileService;

    @Transactional
    public BusinessSignupResponseDto save(
            BusinessSignupRequestDto request,
            String email,
            String passwordHash,
            MultipartFile certificateFile) {
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

            businessMemberProfileRepository.save(
                BusinessMemberProfile.create(
                    member.getId(),
                    request.getManagerName().trim(),
                    request.getManagerPhone().trim(),
                    now
                )
            );

            Organization organization = organizationRepository.save(
                Organization.createBusinessOrganization(
                    request.getOrganizationType(),
                    request.getOrganizationName().trim(),
                    request.getBusinessNumber(),
                    request.getRepresentativeName().trim(),
                    request.getContactEmail().trim(),
                    request.getContactPhone().trim(),
                    blankToNull(request.getPostalCode()),
                    blankToNull(request.getAddressLine1()),
                    blankToNull(request.getAddressLine2()),
                    blankToNull(request.getHomepageUrl()),
                    blankToNull(request.getIntroduction()),
                    null,
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

            if (request.getOrganizationType() == OrganizationType.ORGANIZER) {
                Long certificateFileId = uploadCertificateIfPresent(
                    certificateFile,
                    member.getId()
                );

                organizationSignupReviewRepository.save(
                    OrganizationSignupReview.create(
                        organization.getId(),
                        certificateFileId,
                        now
                    )
                );
            }

            return new BusinessSignupResponseDto(
                member.getId(),
                organization.getId(),
                organization.getStatus()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                GlobalErrorCode.BUSINESS_SIGNUP_CONFLICT
            );
        }
    }

    private Long uploadCertificateIfPresent(MultipartFile certificateFile, Long uploaderId) {
        if (certificateFile == null || certificateFile.isEmpty()) {
            return null;
        }

        return fileService.upload(certificateFile, FileAccessLevel.PRIVATE, uploaderId)
            .getFileId();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
