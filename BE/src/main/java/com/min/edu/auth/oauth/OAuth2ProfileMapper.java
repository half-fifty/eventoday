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
        throw new OAuth2LoginException(
                GlobalErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    private OAuth2ProfileDto mapGoogle(
            Map<String, Object> attributes) {
        String subject = getRequiredValue(attributes, "sub");
        String email = getRequiredValue(attributes, "email");
        String displayName = getOptionalValue(attributes, "name");

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

    private String getRequiredValue(
            Map<String, Object> attributes,
            String key) {
        Object value = attributes.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new OAuth2LoginException(
                    GlobalErrorCode.OAUTH_REQUIRED_ATTRIBUTE_MISSING);
        }

        return value.toString();
    }

    private String getOptionalValue(
            Map<String, Object> attributes,
            String key) {
        Object value = attributes.get(key);

        if (value == null) {
            return null;
        }

        return value.toString();
    }

}
