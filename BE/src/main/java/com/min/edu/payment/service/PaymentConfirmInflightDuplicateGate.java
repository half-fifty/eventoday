package com.min.edu.payment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.min.edu.payment.config.PaymentFinalizationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmInflightDuplicateGate {

    private static final String KEY_PREFIX = "payment:confirm:";
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentFinalizationProperties properties;

    public PaymentConfirmInflightClaim tryClaim(String orderNo) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key(orderNo), token, properties.getConfirmInflightTtl());
            return Boolean.TRUE.equals(acquired)
                ? PaymentConfirmInflightClaim.acquired(token)
                : PaymentConfirmInflightClaim.alreadyInFlight();
        } catch (DataAccessException exception) {
            log.warn("Redis payment confirm in-flight gate failed open: orderNo={}", orderNo, exception);
            return PaymentConfirmInflightClaim.failOpen();
        }
    }

    public void release(String orderNo, String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key(orderNo)), token);
        } catch (DataAccessException exception) {
            log.warn("Redis payment confirm in-flight release failed: orderNo={}", orderNo, exception);
        }
    }

    private String key(String orderNo) {
        return KEY_PREFIX + orderNo;
    }
}
