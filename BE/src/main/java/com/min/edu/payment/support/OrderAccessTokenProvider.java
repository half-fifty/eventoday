package com.min.edu.payment.support;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderAccessTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ORDER_ACCESS_TOKEN_TYPE = "ORDER_ACCESS";

    private final OrderAccessTokenProperties orderAccessTokenProperties;

    public String create(String orderNo, OffsetDateTime expiresAt) {
        Date issuedAt = new Date();

        return Jwts.builder()
            .subject(orderNo)
            .claim(TOKEN_TYPE_CLAIM, ORDER_ACCESS_TOKEN_TYPE)
            .issuedAt(issuedAt)
            .expiration(Date.from(expiresAt.toInstant()))
            .signWith(getSigningKey())
            .compact();
    }

    public String getOrderNo(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!ORDER_ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
        }

        return claims.getSubject();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
        }
    }

    private SecretKey getSigningKey() {
        byte[] secretBytes = orderAccessTokenProperties
            .getSecret()
            .getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(secretBytes);
    }
}
