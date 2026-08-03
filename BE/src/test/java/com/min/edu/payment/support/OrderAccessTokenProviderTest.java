package com.min.edu.payment.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import io.jsonwebtoken.Jwts;

class OrderAccessTokenProviderTest {

    private static final String TEST_SECRET =
        "test-order-access-token-secret-32bytes";
    private static final String OTHER_TEST_SECRET =
        "other-order-access-token-secret-32bytes";

    @Test
    void createAndGetOrderNo_returnsOrderNo() {
        OrderAccessTokenProvider provider = provider(TEST_SECRET);

        String token = provider.create(
            "ORDER-1",
            OffsetDateTime.now().plusMinutes(10)
        );

        assertThat(provider.getOrderNo(token)).isEqualTo("ORDER-1");
    }

    @Test
    void create_doesNotContainPii() {
        OrderAccessTokenProvider provider = provider(TEST_SECRET);

        String token = provider.create(
            "ORDER-1",
            OffsetDateTime.now().plusMinutes(10)
        );

        assertThat(token)
            .doesNotContain("guest@example.com")
            .doesNotContain("010-1234-5678")
            .doesNotContain("guest");
    }

    @Test
    void getOrderNo_failsWhenSignatureIsInvalid() {
        String token = provider(TEST_SECRET).create(
            "ORDER-1",
            OffsetDateTime.now().plusMinutes(10)
        );

        assertThatThrownBy(() -> provider(OTHER_TEST_SECRET).getOrderNo(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
    }

    @Test
    void getOrderNo_failsWhenTokenIsExpired() {
        String token = provider(TEST_SECRET).create(
            "ORDER-1",
            OffsetDateTime.now().minusMinutes(1)
        );

        assertThatThrownBy(() -> provider(TEST_SECRET).getOrderNo(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void getOrderNo_failsWhenTokenIsTampered() {
        String token = provider(TEST_SECRET).create(
            "ORDER-1",
            OffsetDateTime.now().plusMinutes(10)
        );
        String tamperedToken = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> provider(TEST_SECRET).getOrderNo(tamperedToken))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
    }

    @Test
    void getOrderNo_failsWhenTokenTypeIsNotOrderAccess() {
        String token = Jwts.builder()
            .subject("ORDER-1")
            .claim("tokenType", "ACCESS")
            .issuedAt(new java.util.Date())
            .expiration(java.util.Date.from(
                OffsetDateTime.now().plusMinutes(10).toInstant()
            ))
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ))
            .compact();

        assertThatThrownBy(() -> provider(TEST_SECRET).getOrderNo(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
    }

    private OrderAccessTokenProvider provider(String secret) {
        OrderAccessTokenProperties properties = new OrderAccessTokenProperties();
        properties.setSecret(secret);
        return new OrderAccessTokenProvider(properties);
    }
}
