package com.min.edu.auth.oauth;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.min.edu.auth.dto.OAuth2ProfileDto;
import com.min.edu.auth.exception.OAuth2LoginException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.OauthProvider;

@Component
public class OAuth2ProfileMapper {
    public OAuth2ProfileDto map(String registrationId, Map<String, Object> attributes) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return mapGoogle(attributes);
        }
        if ("naver".equalsIgnoreCase(registrationId)) {
            return mapNaver(attributes);
        }
        if ("kakao".equalsIgnoreCase(registrationId)) {
            return mapKakao(attributes);
        }
        throw new OAuth2LoginException(
                GlobalErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    private OAuth2ProfileDto mapGoogle(
            Map<String, Object> attributes) {
        String subject = getRequiredValue(attributes, "sub");
        String email = getRequiredValue(attributes, "email");
        String displayName = getOptionalValue(attributes, "name");

        if (!Boolean.TRUE.equals(attributes.get("email_verified"))) {
            throw new OAuth2LoginException(
                GlobalErrorCode.OAUTH_EMAIL_NOT_VERIFIED
            );
        }

        if (displayName == null || displayName.isBlank()) {
            displayName = "사용자";
        }

        return OAuth2ProfileDto.builder()
                .provider(OauthProvider.GOOGLE)
                .subject(subject)
                .email(email)
                .displayName(displayName)
                .build();
    }

    private OAuth2ProfileDto mapNaver(
            Map<String, Object> attributes) {
        Object responseValue = attributes.get("response");

        if (!(responseValue instanceof Map<?, ?> response)) {
            throw new OAuth2LoginException(
                GlobalErrorCode.OAUTH_REQUIRED_ATTRIBUTE_MISSING
            );
        }

        String subject = getRequiredValue(response, "id");
        String email = getRequiredValue(response, "email");
        String name = getRequiredValue(response, "name");
        String nickname = getOptionalValue(response, "nickname");

        String displayName = nickname;
        if (displayName == null || displayName.isBlank()) {
            displayName = name;
        }

        return OAuth2ProfileDto.builder()
            .provider(OauthProvider.NAVER)
            .subject(subject)
            .email(email)
            .displayName(displayName)
            .build();
    }

    private OAuth2ProfileDto mapKakao(
            Map<String, Object> attributes) {
        String subject = getRequiredValue(attributes, "id");
        Map<?, ?> kakaoAccount = getRequiredMap(
            attributes,
            "kakao_account"
        );
        String email = getOptionalValue(kakaoAccount, "email");

        if (email != null && !email.isBlank()
                && (!Boolean.TRUE.equals(
                    kakaoAccount.get("is_email_valid")
                ) || !Boolean.TRUE.equals(
                    kakaoAccount.get("is_email_verified")
                ))) {
            throw new OAuth2LoginException(
                GlobalErrorCode.OAUTH_EMAIL_NOT_VERIFIED
            );
        }

        if (email == null || email.isBlank()) {
            email = "kakao-" + subject + "@oauth.invalid";
        }

        String displayName = "사용자";
        Object profileValue = kakaoAccount.get("profile");

        if (profileValue instanceof Map<?, ?> profile) {
            String nickname = getOptionalValue(profile, "nickname");
            if (nickname != null && !nickname.isBlank()) {
                displayName = nickname;
            }
        }

        return OAuth2ProfileDto.builder()
            .provider(OauthProvider.KAKAO)
            .subject(subject)
            .email(email)
            .displayName(displayName)
            .build();
    }

    private Map<?, ?> getRequiredMap(
            Map<?, ?> attributes,
            String key) {
        Object value = attributes.get(key);

        if (!(value instanceof Map<?, ?> nestedAttributes)) {
            throw new OAuth2LoginException(
                GlobalErrorCode.OAUTH_REQUIRED_ATTRIBUTE_MISSING
            );
        }

        return nestedAttributes;
    }

    private String getRequiredValue(
            Map<?, ?> attributes,
            String key) {
        Object value = attributes.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new OAuth2LoginException(
                    GlobalErrorCode.OAUTH_REQUIRED_ATTRIBUTE_MISSING);
        }

        return value.toString();
    }

    private String getOptionalValue(
            Map<?, ?> attributes,
            String key) {
        Object value = attributes.get(key);

        if (value == null) {
            return null;
        }

        return value.toString();
    }

}
