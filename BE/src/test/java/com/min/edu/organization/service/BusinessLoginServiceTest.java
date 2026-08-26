package com.min.edu.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.min.edu.auth.dto.TokenDto;
import com.min.edu.auth.service.RefreshTokenService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationType;
import com.min.edu.organization.dto.BusinessLoginRequestDto;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;

@ExtendWith(MockitoExtension.class)
class BusinessLoginServiceTest {

    private static final String EMAIL = "owner@example.com";
    private static final String RAW_PASSWORD = "password1234";
    private static final String HASH = "encoded-hash";

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository organizationMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;

    private BusinessLoginService service() {
        return new BusinessLoginService(
            organizationRepository, organizationMemberRepository, memberRepository,
            passwordEncoder, jwtTokenProvider, refreshTokenService
        );
    }

    private Member member() {
        return Member.createBusinessMember(EMAIL, "부스참가사", HASH, OffsetDateTime.now());
    }

    private Organization organization(OrganizationType type, OrganizationStatusSetter statusSetter) {
        Organization organization = Organization.createBusinessOrganization(
            type, "부스참가사", "1234567890", "홍길동", "contact@example.com", "0212345678",
            null, null, null, null, null, null, OffsetDateTime.now()
        );
        statusSetter.apply(organization);
        return organization;
    }

    private interface OrganizationStatusSetter {
        void apply(Organization organization);
    }

    private BusinessLoginRequestDto request() {
        BusinessLoginRequestDto request = new BusinessLoginRequestDto();
        setField(request, "email", EMAIL);
        setField(request, "password", RAW_PASSWORD);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void stubMemberAndOrganization(Member member, Organization organization) {
        given(memberRepository.findByEmail(EMAIL)).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, HASH)).willReturn(true);
        OrganizationMember organizationMember = OrganizationMember.createOwner(
            10L, member.getId(), OffsetDateTime.now()
        );
        given(organizationMemberRepository.findFirstByMemberIdAndStatusOrderByIdAsc(
            member.getId(), OrganizationMemberStatus.ACTIVE
        )).willReturn(java.util.Optional.of(organizationMember));
        given(organizationRepository.findById(10L)).willReturn(java.util.Optional.of(organization));
    }

    @Test
    void login_activeOrganization_issuesTokens() {
        Member member = member();
        Organization organization = organization(OrganizationType.EXHIBITOR, o -> { });
        stubMemberAndOrganization(member, organization);
        given(jwtTokenProvider.createAccessToken(member.getId(), PlatformRole.USER))
            .willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(member.getId())).willReturn("refresh-token");

        TokenDto tokenDto = service().login(request());

        assertThat(tokenDto.getAccessToken()).isEqualTo("access-token");
        assertThat(tokenDto.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        given(memberRepository.findByEmail(EMAIL)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().login(request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_BUSINESS_CREDENTIALS);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        given(memberRepository.findByEmail(EMAIL)).willReturn(java.util.Optional.of(member()));
        given(passwordEncoder.matches(RAW_PASSWORD, HASH)).willReturn(false);

        assertThatThrownBy(() -> service().login(request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_BUSINESS_CREDENTIALS);
    }

    @Test
    void login_pendingOrganization_throwsApprovalPending() {
        Member member = member();
        Organization organization = organization(OrganizationType.ORGANIZER, o -> { });
        stubMemberAndOrganization(member, organization);

        assertThatThrownBy(() -> service().login(request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.ORGANIZATION_APPROVAL_PENDING);
    }

    @Test
    void login_suspendedOrganization_throwsSuspended() {
        Member member = member();
        Organization organization = organization(
            OrganizationType.EXHIBITOR,
            o -> o.suspend(OffsetDateTime.now())
        );
        stubMemberAndOrganization(member, organization);

        assertThatThrownBy(() -> service().login(request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.ORGANIZATION_SUSPENDED);
    }
}
