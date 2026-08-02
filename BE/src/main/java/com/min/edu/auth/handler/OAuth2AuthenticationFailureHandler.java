package com.min.edu.auth.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.min.edu.auth.exception.OAuth2LoginException;
import com.min.edu.common.exception.GlobalErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final String frontendUrl;

    public OAuth2AuthenticationFailureHandler(
            @Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        GlobalErrorCode errorCode = GlobalErrorCode.OAUTH_LOGIN_FAILED;

        if (authenticationException instanceof OAuth2LoginException) {
            OAuth2LoginException oauth2LoginException =
                (OAuth2LoginException) authenticationException;
            errorCode = oauth2LoginException.getErrorCode();
        }

        String encodedErrorCode = URLEncoder.encode(
            errorCode.getCode(),
            StandardCharsets.UTF_8
        );

        response.sendRedirect(frontendUrl + "/login?error=" + encodedErrorCode);
    }
}
