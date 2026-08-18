package com.min.edu.funnel.support;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class AnonymousIdCookieFactory {

    private static final String ANONYMOUS_ID_COOKIE = "anonymousId";
    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE = "Lax";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private final boolean cookieSecure;

    public AnonymousIdCookieFactory(@Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ANONYMOUS_ID_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String issue() {
        return UUID.randomUUID().toString();
    }

    public ResponseCookie createCookie(String anonymousId) {
        return ResponseCookie.from(ANONYMOUS_ID_COOKIE, anonymousId)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(SAME_SITE)
            .path(COOKIE_PATH)
            .maxAge(COOKIE_MAX_AGE)
            .build();
    }
}
