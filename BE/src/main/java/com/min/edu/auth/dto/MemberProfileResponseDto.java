package com.min.edu.auth.dto;

import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.domain.OauthProvider;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationMember;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileResponseDto {

    private Long memberId;
    private String email;
    private String nickname;
    private OauthProvider oauthProvider;
    private MemberAccountType accountType;
    private PlatformRole platformRole;
    private MemberStatus status;
    private OrganizationProfileResponseDto organization;

    public static MemberProfileResponseDto fromSocialMember(Member member) {
        return MemberProfileResponseDto.builder()
            .memberId(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .oauthProvider(member.getOauthProvider())
            .accountType(MemberAccountType.SOCIAL)
            .platformRole(member.getPlatformRole())
            .status(member.getStatus())
            .build();
    }

    public static MemberProfileResponseDto fromBusinessMember(
            Member member,
            Organization organization,
            OrganizationMember organizationMember) {
        return MemberProfileResponseDto.builder()
            .memberId(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .oauthProvider(member.getOauthProvider())
            .accountType(MemberAccountType.BUSINESS)
            .platformRole(member.getPlatformRole())
            .status(member.getStatus())
            .organization(
                OrganizationProfileResponseDto.from(
                    organization,
                    organizationMember
                )
            )
            .build();
    }
}
