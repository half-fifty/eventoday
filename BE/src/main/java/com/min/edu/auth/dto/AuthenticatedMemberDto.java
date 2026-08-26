package com.min.edu.auth.dto;

import com.min.edu.member.domain.PlatformRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedMemberDto {

    private Long memberId;
    private PlatformRole platformRole;
}
