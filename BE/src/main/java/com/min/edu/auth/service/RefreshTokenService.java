package com.min.edu.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.min.edu.common.security.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void save(Long memberId, String refreshToken) {
        String key = createKey(memberId);
        Duration expiration = Duration.ofMillis(
            jwtTokenProvider.getRefreshTokenExpiration()
        );

        stringRedisTemplate.opsForValue().set(
            key,
            refreshToken,
            expiration
        );
    }

    public boolean matches(Long memberId, String refreshToken) {
        String savedRefreshToken = stringRedisTemplate
            .opsForValue()
            .get(createKey(memberId));

        if (savedRefreshToken == null) {
            return false;
        }

        return savedRefreshToken.equals(refreshToken);
    }

    public void delete(Long memberId) {
        stringRedisTemplate.delete(createKey(memberId));
    }

    private String createKey(Long memberId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId;
    }
}
