package com.min.edu.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.admin.service.PlatformAuditService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.organization.domain.OrganizationSignupReview;
import com.min.edu.organization.domain.OrganizationType;
import com.min.edu.organization.repository.BusinessMemberProfileRepository;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;
import com.min.edu.organization.repository.OrganizationSignupReviewRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationSignupReviewAdminServiceTest {

    @Mock private OrganizationSignupReviewRepository reviewRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository organizationMemberRepository;
    @Mock private BusinessMemberProfileRepository businessMemberProfileRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private FileService fileService;
    @Mock private PlatformAuditService platformAuditService;

    private OrganizationSignupReviewAdminService service() {
        return new OrganizationSignupReviewAdminService(
            reviewRepository, organizationRepository, organizationMemberRepository,
            businessMemberProfileRepository, memberRepository, fileService, platformAuditService
        );
    }

    private AuthenticatedMemberDto admin() {
        return new AuthenticatedMemberDto(99L, PlatformRole.PLATFORM_ADMIN);
    }

    private AuthenticatedMemberDto normalUser() {
        return new AuthenticatedMemberDto(5L, PlatformRole.USER);
    }

    private Organization pendingOrganization() {
        return Organization.createBusinessOrganization(
            OrganizationType.ORGANIZER, "행사개최사", "1234567890", "김철수",
            "contact@example.com", "0212345678",
            null, null, null, null, null, null, OffsetDateTime.now()
        );
    }

    @Test
    void list_batchLoadsOrganizationsAndProfilesInsteadOfPerRowQueries() {
        Organization organization = pendingOrganization();
        OrganizationSignupReview review =
            OrganizationSignupReview.create(organization.getId(), null, OffsetDateTime.now());
        OrganizationMember owner = OrganizationMember.createOwner(organization.getId(), 7L, OffsetDateTime.now());
        com.min.edu.organization.domain.BusinessMemberProfile profile =
            com.min.edu.organization.domain.BusinessMemberProfile.create(
                7L, "담당자", "01000000000", OffsetDateTime.now()
            );

        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(0, 20);
        given(reviewRepository.findByStatus(
            com.min.edu.organization.domain.OrganizationSignupReviewStatus.PENDING, pageable
        )).willReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(review)));
        given(organizationRepository.findAllById(org.mockito.ArgumentMatchers.<Long>anyIterable()))
            .willReturn(java.util.List.of(organization));
        given(organizationMemberRepository.findByOrganizationIdInAndOrganizationRole(
            org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.eq(OrganizationRole.OWNER)
        )).willReturn(java.util.List.of(owner));
        given(businessMemberProfileRepository.findAllById(java.util.List.of(7L)))
            .willReturn(java.util.List.of(profile));

        var page = service().list(
            com.min.edu.organization.domain.OrganizationSignupReviewStatus.PENDING, admin(), pageable
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getManagerName()).isEqualTo("담당자");
        assertThat(page.getContent().get(0).getOrganizationName()).isEqualTo(organization.getName());
        org.mockito.Mockito.verify(organizationMemberRepository, org.mockito.Mockito.never())
            .findByOrganizationIdAndOrganizationRole(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    void approve_nonAdminActor_throwsForbidden() {
        assertThatThrownBy(() -> service().approve(1L, normalUser()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void approve_nullActor_throwsUnauthorized() {
        assertThatThrownBy(() -> service().approve(1L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.UNAUTHORIZED);
    }

    @Test
    void approve_pendingReview_movesReviewAndOrganizationToApproved() {
        OrganizationSignupReview review = OrganizationSignupReview.create(10L, null, OffsetDateTime.now());
        Organization organization = pendingOrganization();
        given(reviewRepository.findByIdForUpdate(1L)).willReturn(Optional.of(review));
        given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));

        service().approve(1L, admin());

        assertThat(organization.getStatus().name()).isEqualTo("ACTIVE");
        verify(platformAuditService).record(
            eq(99L), eq("ORGANIZATION_SIGNUP"), eq("APPROVED"),
            eq(organization.getId()), eq(organization.getName()), isNull()
        );
    }

    @Test
    void approve_alreadyProcessedReview_throwsInvalidInputValue() {
        OrganizationSignupReview review = OrganizationSignupReview.create(10L, null, OffsetDateTime.now());
        review.approve(1L, OffsetDateTime.now());
        Organization organization = pendingOrganization();
        given(reviewRepository.findByIdForUpdate(1L)).willReturn(Optional.of(review));
        given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));

        assertThatThrownBy(() -> service().approve(1L, admin()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void reject_blankReason_throwsRejectionReasonRequired() {
        assertThatThrownBy(() -> service().reject(1L, "  ", admin()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", GlobalErrorCode.ORGANIZATION_REJECTION_REASON_REQUIRED
            );
    }

    @Test
    void reject_pendingReview_deletesSignupDataSoTheBusinessNumberCanBeReused() {
        OrganizationSignupReview review = OrganizationSignupReview.create(10L, 55L, OffsetDateTime.now());
        Organization organization = pendingOrganization();
        OrganizationMember owner = OrganizationMember.createOwner(10L, 7L, OffsetDateTime.now());
        given(reviewRepository.findByIdForUpdate(1L)).willReturn(Optional.of(review));
        given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));
        given(organizationMemberRepository.findByOrganizationIdAndOrganizationRole(
            organization.getId(), OrganizationRole.OWNER
        )).willReturn(Optional.of(owner));

        service().reject(1L, "정보가 일치하지 않습니다.", admin());

        verify(platformAuditService).record(
            eq(99L), eq("ORGANIZATION_SIGNUP"), eq("REJECTED"),
            eq(organization.getId()), eq(organization.getName()), eq("정보가 일치하지 않습니다.")
        );
        verify(reviewRepository).deleteAllByOrganizationId(organization.getId());
        verify(businessMemberProfileRepository).deleteById(7L);
        verify(organizationMemberRepository).delete(owner);
        verify(organizationRepository).delete(organization);
        verify(memberRepository).deleteById(7L);
        verify(fileService).deleteFile(55L);
    }

    @Test
    void reject_reviewWithoutCertificate_doesNotCallFileService() {
        OrganizationSignupReview review = OrganizationSignupReview.create(10L, null, OffsetDateTime.now());
        Organization organization = pendingOrganization();
        OrganizationMember owner = OrganizationMember.createOwner(10L, 7L, OffsetDateTime.now());
        given(reviewRepository.findByIdForUpdate(1L)).willReturn(Optional.of(review));
        given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));
        given(organizationMemberRepository.findByOrganizationIdAndOrganizationRole(
            organization.getId(), OrganizationRole.OWNER
        )).willReturn(Optional.of(owner));

        service().reject(1L, "사유", admin());

        org.mockito.Mockito.verifyNoInteractions(fileService);
    }

    @Test
    void reject_alreadyProcessedReview_throwsInvalidInputValue() {
        OrganizationSignupReview review = OrganizationSignupReview.create(10L, null, OffsetDateTime.now());
        review.approve(1L, OffsetDateTime.now());
        given(reviewRepository.findByIdForUpdate(1L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> service().reject(1L, "사유", admin()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_INPUT_VALUE);
    }
}
