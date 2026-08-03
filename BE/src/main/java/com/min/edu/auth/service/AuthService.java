package com.min.edu.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.MemberProfileResponseDto;
import com.min.edu.auth.dto.TokenDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.organization.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public MemberProfileResponseDto getCurrentMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        OrganizationMember organizationMember = organizationMemberRepository
            .findFirstByMemberIdAndStatusOrderByIdAsc(
                memberId,
                OrganizationMemberStatus.ACTIVE
            )
            .orElse(null);

        if (organizationMember == null) {
            return MemberProfileResponseDto.fromSocialMember(member);
        }

        Organization organization = organizationRepository
            .findById(organizationMember.getOrganizationId())
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.ENTITY_NOT_FOUND
            ));

        return MemberProfileResponseDto.fromBusinessMember(
            member,
            organization,
            organizationMember
        );
    }

    @Transactional
    public TokenDto reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.REFRESH_TOKEN_REQUIRED);
        }

        Long memberId = getMemberIdFromRefreshToken(refreshToken);

        if (!refreshTokenService.matches(memberId, refreshToken)) {
            throw new BusinessException(GlobalErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(
            member.getId(),
            member.getPlatformRole()
        );
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        refreshTokenService.save(member.getId(), newRefreshToken);

        return TokenDto.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .build();
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        if (!jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return;
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);

        if (refreshTokenService.matches(memberId, refreshToken)) {
            refreshTokenService.delete(memberId);
        }
    }

    private Long getMemberIdFromRefreshToken(String refreshToken) {
        try {
            if (!jwtTokenProvider.validateToken(refreshToken)
                    || !jwtTokenProvider.isRefreshToken(refreshToken)) {
                throw new BusinessException(GlobalErrorCode.INVALID_REFRESH_TOKEN);
            }

            return jwtTokenProvider.getMemberId(refreshToken);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
