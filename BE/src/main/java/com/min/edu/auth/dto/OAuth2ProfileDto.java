package com.min.edu.auth.dto;

import com.min.edu.member.domain.OauthProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2ProfileDto {
    private OauthProvider provider;
    private String subject;
    private String email;
    private String displayName;
    
}
