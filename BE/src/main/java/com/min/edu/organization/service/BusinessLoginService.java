package com.min.edu.organization.service;

import java.time.OffsetDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.TokenDto;
import com.min.edu.auth.service.RefreshTokenService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.dto.BusinessLoginRequestDto;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessLoginService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenDto login(BusinessLoginRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();

        Member member = memberRepository
            .findByEmail(email)
            .orElseThrow(() -> invalidCredentials());

        if (member.getPasswordHash() == null
                || !passwordEncoder.matches(
                    request.getPassword(),
                    member.getPasswordHash()
                )) {
            throw invalidCredentials();
        }

        OrganizationMember organizationMember = organizationMemberRepository
            .findFirstByMemberIdAndStatusOrderByIdAsc(
                member.getId(),
                OrganizationMemberStatus.ACTIVE
            )
            .orElseThrow(() -> invalidCredentials());

        Organization organization = organizationRepository
            .findById(organizationMember.getOrganizationId())
            .orElseThrow(() -> invalidCredentials());

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(
                GlobalErrorCode.MEMBER_LOGIN_RESTRICTED
            );
        }

        requireLoginableOrganization(organization);

        member.updateLastLoginAt(OffsetDateTime.now());

        String accessToken = jwtTokenProvider.createAccessToken(
            member.getId(),
            member.getPlatformRole()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(
            member.getId()
        );

        refreshTokenService.save(member.getId(), refreshToken);

        return TokenDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
    }

    private void requireLoginableOrganization(Organization organization) {
        switch (organization.getStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw new BusinessException(
                GlobalErrorCode.ORGANIZATION_APPROVAL_PENDING
            );
            case SUSPENDED -> throw new BusinessException(
                GlobalErrorCode.ORGANIZATION_SUSPENDED
            );
            default -> throw new BusinessException(
                GlobalErrorCode.MEMBER_LOGIN_RESTRICTED
            );
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
            GlobalErrorCode.INVALID_BUSINESS_CREDENTIALS
        );
    }
}
