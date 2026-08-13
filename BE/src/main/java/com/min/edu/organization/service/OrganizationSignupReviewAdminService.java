package com.min.edu.organization.service;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.admin.service.PlatformAuditService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.BusinessMemberProfile;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.organization.domain.OrganizationSignupReview;
import com.min.edu.organization.domain.OrganizationSignupReviewStatus;
import com.min.edu.organization.dto.OrganizationSignupReviewDetailDto;
import com.min.edu.organization.dto.OrganizationSignupReviewSummaryDto;
import com.min.edu.organization.repository.BusinessMemberProfileRepository;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;
import com.min.edu.organization.repository.OrganizationSignupReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationSignupReviewAdminService {

    private final OrganizationSignupReviewRepository reviewRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final BusinessMemberProfileRepository businessMemberProfileRepository;
    private final MemberRepository memberRepository;
    private final FileService fileService;
    private final PlatformAuditService platformAuditService;

    public Page<OrganizationSignupReviewSummaryDto> list(
            OrganizationSignupReviewStatus status,
            AuthenticatedMemberDto actor,
            Pageable pageable) {
        requireAdmin(actor);

        Page<OrganizationSignupReview> reviews = status != null
            ? reviewRepository.findByStatus(status, pageable)
            : reviewRepository.findAll(pageable);

        return reviews.map(this::toSummary);
    }

    public OrganizationSignupReviewDetailDto getDetail(Long reviewId, AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        OrganizationSignupReview review = getReview(reviewId);
        Organization organization = getOrganization(review.getOrganizationId());
        OrganizationMember owner = getOwner(organization.getId());
        BusinessMemberProfile profile = businessMemberProfileRepository
            .findById(owner.getMemberId())
            .orElse(null);

        String certificateDownloadUrl = review.getBusinessRegistrationFileId() != null
            ? fileService.getFileDownloadUrlForAdmin(review.getBusinessRegistrationFileId())
            : null;

        return new OrganizationSignupReviewDetailDto(
            review.getId(),
            organization.getId(),
            organization.getName(),
            organization.getRepresentativeName(),
            maskBusinessNumber(organization.getBusinessNumber()),
            organization.getContactEmail(),
            organization.getContactPhone(),
            organization.getContactEmail(),
            profile != null ? profile.getContactName() : null,
            profile != null ? profile.getContactPhone() : null,
            organization.getPostalCode(),
            organization.getAddressLine1(),
            organization.getAddressLine2(),
            organization.getHomepageUrl(),
            organization.getIntroduction(),
            review.getBusinessRegistrationFileId(),
            certificateDownloadUrl,
            review.getSubmittedAt(),
            review.getStatus(),
            review.getReviewedBy(),
            review.getReviewedAt(),
            review.getRejectionReason()
        );
    }

    @Transactional
    public void approve(Long reviewId, AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        OrganizationSignupReview review = getReview(reviewId);
        Organization organization = getOrganization(review.getOrganizationId());
        OffsetDateTime now = OffsetDateTime.now();

        transition(() -> review.approve(actor.getMemberId(), now));
        transition(() -> organization.approve(now));

        platformAuditService.record(
            actor.getMemberId(), "ORGANIZATION_SIGNUP", "APPROVED",
            organization.getId(), organization.getName(), null
        );
    }

    /**
     * 반려 = 가입 신청 취소. 상태만 바꾸는 대신 이번 신청 관련 데이터를 모두 삭제해서
     * 같은 사업자등록번호·이메일로 처음부터 다시 가입할 수 있게 한다.
     * 반려 사실 자체는 platformAuditService 감사 로그에 별도로 남는다.
     */
    @Transactional
    public void reject(Long reviewId, String reason, AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORGANIZATION_REJECTION_REASON_REQUIRED);
        }

        OrganizationSignupReview review = getReview(reviewId);
        if (review.getStatus() != OrganizationSignupReviewStatus.PENDING) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        Organization organization = getOrganization(review.getOrganizationId());
        OrganizationMember owner = getOwner(organization.getId());
        Long ownerMemberId = owner.getMemberId();

        platformAuditService.record(
            actor.getMemberId(), "ORGANIZATION_SIGNUP", "REJECTED",
            organization.getId(), organization.getName(), reason
        );

        reviewRepository.deleteAllByOrganizationId(organization.getId());
        businessMemberProfileRepository.deleteById(ownerMemberId);
        organizationMemberRepository.delete(owner);
        organizationRepository.delete(organization);
        memberRepository.deleteById(ownerMemberId);
    }

    private OrganizationSignupReviewSummaryDto toSummary(OrganizationSignupReview review) {
        Organization organization = getOrganization(review.getOrganizationId());
        OrganizationMember owner = getOwner(organization.getId());
        BusinessMemberProfile profile = businessMemberProfileRepository
            .findById(owner.getMemberId())
            .orElse(null);

        return new OrganizationSignupReviewSummaryDto(
            review.getId(),
            organization.getId(),
            organization.getName(),
            organization.getRepresentativeName(),
            maskBusinessNumber(organization.getBusinessNumber()),
            profile != null ? profile.getContactName() : null,
            review.getSubmittedAt(),
            review.getStatus()
        );
    }

    private String maskBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.length() != 10) {
            return "**********";
        }
        return businessNumber.substring(0, 3) + "***" + businessNumber.substring(6);
    }

    private OrganizationMember getOwner(Long organizationId) {
        return organizationMemberRepository
            .findByOrganizationIdAndOrganizationRole(organizationId, OrganizationRole.OWNER)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    private OrganizationSignupReview getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.ORGANIZATION_SIGNUP_REVIEW_NOT_FOUND
            ));
    }

    private Organization getOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
