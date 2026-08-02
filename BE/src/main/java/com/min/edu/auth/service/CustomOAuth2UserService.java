package com.min.edu.auth.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.OAuth2ProfileDto;
import com.min.edu.auth.exception.OAuth2LoginException;
import com.min.edu.auth.oauth.OAuth2ProfileMapper;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2ProfileMapper oauth2ProfileMapper;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest
            .getClientRegistration()
            .getRegistrationId();

        OAuth2ProfileDto profileDto = oauth2ProfileMapper.map(
            registrationId,
            oauth2User.getAttributes()
        );

        Optional<Member> optionalMember = memberRepository
            .findByOauthProviderAndOauthSubject(
                profileDto.getProvider(),
                profileDto.getSubject()
            );

        OffsetDateTime now = OffsetDateTime.now();
        Member member;

        if (optionalMember.isPresent()) {
            member = optionalMember.get();

            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new OAuth2LoginException(
                    GlobalErrorCode.MEMBER_LOGIN_RESTRICTED
                );
            }

            member.updateOAuthProfile(
                profileDto.getEmail(),
                profileDto.getDisplayName(),
                now
            );
        } else {
            member = Member.createOAuthMember(
                profileDto.getEmail(),
                profileDto.getDisplayName(),
                profileDto.getProvider(),
                profileDto.getSubject(),
                now
            );
            memberRepository.save(member);
        }

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("memberId", member.getId());
        attributes.put("platformRole", member.getPlatformRole().name());

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + member.getPlatformRole().name())),
            attributes,
            "memberId"
        );
    }
}
