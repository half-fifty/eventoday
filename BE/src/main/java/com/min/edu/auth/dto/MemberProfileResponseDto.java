package com.min.edu.auth.dto;

import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.domain.OauthProvider;
import com.min.edu.member.domain.PlatformRole;

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
    private PlatformRole platformRole;
    private MemberStatus status;

    public static MemberProfileResponseDto from(Member member) {
        return MemberProfileResponseDto.builder()
            .memberId(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .oauthProvider(member.getOauthProvider())
            .platformRole(member.getPlatformRole())
            .status(member.getStatus())
            .build();
    }
}
