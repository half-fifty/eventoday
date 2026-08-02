package com.min.edu.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.min.edu.member.domain.PlatformRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long memberId, PlatformRole platformRole) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(
            issuedAt.getTime() + jwtProperties.getAccessTokenExpiration()
        );

        return Jwts.builder()
            .subject(String.valueOf(memberId))
            .claim(ROLE_CLAIM, platformRole.name())
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issuedAt(issuedAt)
            .expiration(expiresAt)
            .signWith(getSigningKey())
            .compact();
    }

    public String createRefreshToken(Long memberId) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(
            issuedAt.getTime() + jwtProperties.getRefreshTokenExpiration()
        );

        return Jwts.builder()
            .subject(String.valueOf(memberId))
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .issuedAt(issuedAt)
            .expiration(expiresAt)
            .signWith(getSigningKey())
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public PlatformRole getPlatformRole(String token) {
        String role = parseClaims(token).get(ROLE_CLAIM, String.class);
        return PlatformRole.valueOf(role);
    }

    public boolean isAccessToken(String token) {
        String tokenType = parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
        return ACCESS_TOKEN_TYPE.equals(tokenType);
    }

    public boolean isRefreshToken(String token) {
        String tokenType = parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
        return REFRESH_TOKEN_TYPE.equals(tokenType);
    }

    public long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    public long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] secretBytes = jwtProperties
            .getSecret()
            .getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(secretBytes);
    }
}
