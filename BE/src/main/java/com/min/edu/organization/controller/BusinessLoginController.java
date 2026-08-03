package com.min.edu.organization.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.cookie.AuthCookieFactory;
import com.min.edu.auth.dto.TokenDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.organization.dto.BusinessLoginRequestDto;
import com.min.edu.organization.service.BusinessLoginService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/business")
@RequiredArgsConstructor
public class BusinessLoginController {

    private final BusinessLoginService businessLoginService;
    private final AuthCookieFactory authCookieFactory;

    @PostMapping("/login")
    public ApiResponse<Void> login(
            @Valid @RequestBody BusinessLoginRequestDto request,
            HttpServletResponse response) {
        TokenDto tokenDto = businessLoginService.login(request);
        ResponseCookie accessTokenCookie =
            authCookieFactory.createAccessTokenCookie(
                tokenDto.getAccessToken()
            );
        ResponseCookie refreshTokenCookie =
            authCookieFactory.createRefreshTokenCookie(
                tokenDto.getRefreshToken()
            );

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            accessTokenCookie.toString()
        );
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookie.toString()
        );

        return ApiResponse.success();
    }
}
