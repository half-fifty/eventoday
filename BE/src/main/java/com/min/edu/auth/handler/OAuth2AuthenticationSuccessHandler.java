package com.min.edu.auth.handler;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.min.edu.auth.cookie.AuthCookieFactory;
import com.min.edu.auth.service.RefreshTokenService;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.member.domain.PlatformRole;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieFactory authCookieFactory;
    private final String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            AuthCookieFactory authCookieFactory,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.authCookieFactory = authCookieFactory;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        Long memberId = getMemberId(oauth2User);
        PlatformRole platformRole = getPlatformRole(oauth2User);

        String accessToken = jwtTokenProvider.createAccessToken(
            memberId,
            platformRole
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        refreshTokenService.save(memberId, refreshToken);

        ResponseCookie accessTokenCookie =
            authCookieFactory.createAccessTokenCookie(accessToken);
        ResponseCookie refreshTokenCookie =
            authCookieFactory.createRefreshTokenCookie(refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        response.sendRedirect(frontendUrl);
    }

    private Long getMemberId(OAuth2User oauth2User) {
        Object memberId = oauth2User.getAttribute("memberId");

        if (memberId == null) {
            throw new IllegalStateException(
                "OAuth 로그인 사용자 정보에 memberId가 없습니다."
            );
        }

        if (memberId instanceof Number) {
            Number number = (Number) memberId;
            return number.longValue();
        }

        return Long.valueOf(String.valueOf(memberId));
    }

    private PlatformRole getPlatformRole(OAuth2User oauth2User) {
        Object platformRole = oauth2User.getAttribute("platformRole");

        if (platformRole == null) {
            throw new IllegalStateException(
                "OAuth 로그인 사용자 정보에 platformRole이 없습니다."
            );
        }

        try {
            return PlatformRole.valueOf(String.valueOf(platformRole));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "OAuth 로그인 사용자의 platformRole이 올바르지 않습니다.",
                exception
            );
        }
    }
}
