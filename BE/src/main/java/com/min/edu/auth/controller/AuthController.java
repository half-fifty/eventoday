package com.min.edu.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.cookie.AuthCookieFactory;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.auth.dto.MemberProfileResponseDto;
import com.min.edu.auth.dto.TokenDto;
import com.min.edu.auth.service.AuthService;
import com.min.edu.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;

    public AuthController(
            AuthService authService,
            AuthCookieFactory authCookieFactory) {
        this.authService = authService;
        this.authCookieFactory = authCookieFactory;
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

        ResponseCookie accessTokenCookie = authCookieFactory.createAccessTokenCookie(
            tokenDto.getAccessToken()
        );
        ResponseCookie refreshTokenCookie = authCookieFactory.createRefreshTokenCookie(
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
            authCookieFactory.createExpiredAccessTokenCookie().toString()
        );
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            authCookieFactory.createExpiredRefreshTokenCookie().toString()
        );

        return ApiResponse.success();
    }
}
