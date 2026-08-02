package com.min.edu.auth.cookie;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.min.edu.common.security.jwt.JwtTokenProvider;

@Component
public class AuthCookieFactory {

    private static final String ACCESS_TOKEN_NAME = "accessToken";
    private static final String REFRESH_TOKEN_NAME = "refreshToken";
    private static final String ACCESS_TOKEN_PATH = "/";
    private static final String REFRESH_TOKEN_PATH = "/api/auth";
    private static final String SAME_SITE = "Lax";

    private final JwtTokenProvider jwtTokenProvider;
    private final boolean cookieSecure;

    public AuthCookieFactory(
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieSecure = cookieSecure;
    }

    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return createCookie(
            ACCESS_TOKEN_NAME,
            accessToken,
            ACCESS_TOKEN_PATH,
            Duration.ofMillis(jwtTokenProvider.getAccessTokenExpiration())
        );
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return createCookie(
            REFRESH_TOKEN_NAME,
            refreshToken,
            REFRESH_TOKEN_PATH,
            Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration())
        );
    }

    public ResponseCookie createExpiredAccessTokenCookie() {
        return createCookie(
            ACCESS_TOKEN_NAME,
            "",
            ACCESS_TOKEN_PATH,
            Duration.ZERO
        );
    }

    public ResponseCookie createExpiredRefreshTokenCookie() {
        return createCookie(
            REFRESH_TOKEN_NAME,
            "",
            REFRESH_TOKEN_PATH,
            Duration.ZERO
        );
    }

    private ResponseCookie createCookie(
            String name,
            String value,
            String path,
            Duration maxAge) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(SAME_SITE)
            .path(path)
            .maxAge(maxAge)
            .build();
    }
}
