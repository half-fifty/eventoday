package com.min.edu.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.auth.dto.MemberProfileResponseDto;
import com.min.edu.auth.dto.TokenDto;
import com.min.edu.auth.service.AuthService;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.common.security.jwt.JwtTokenProvider;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/me")
    public ApiResponse<MemberProfileResponseDto> getCurrentMember(
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {
        MemberProfileResponseDto response = authService.getCurrentMember(
            authenticatedMember.getMemberId()
        );

        return ApiResponse.success(response);
    }

    @PostMapping("/reissue")
    public ApiResponse<Void> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        TokenDto tokenDto = authService.reissue(refreshToken);

        ResponseCookie accessTokenCookie = createAccessTokenCookie(
            tokenDto.getAccessToken()
        );
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(
            tokenDto.getRefreshToken()
        );

        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return ApiResponse.success();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            createExpiredCookie("accessToken", "/").toString()
        );
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            createExpiredCookie("refreshToken", "/api/auth").toString()
        );

        return ApiResponse.success();
    }

    private ResponseCookie createAccessTokenCookie(String accessToken) {
        return ResponseCookie.from("accessToken", accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofMillis(jwtTokenProvider.getAccessTokenExpiration()))
            .build();
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration()))
            .build();
    }

    private ResponseCookie createExpiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path(path)
            .maxAge(Duration.ZERO)
            .build();
    }
}
