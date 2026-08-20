package com.min.edu.payment.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.min.edu.payment.config.TicketOrderReliabilityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOrderInflightDuplicateGate {

    private static final String KEY_PREFIX = "ticket-order:idempotency:";

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketOrderReliabilityProperties properties;

    public InflightClaimResult tryClaim(String idempotencyKey) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(
                    key(idempotencyKey),
                    "1",
                    properties.getInflightTtl()
                );
            return Boolean.TRUE.equals(acquired)
                ? InflightClaimResult.ACQUIRED
                : InflightClaimResult.ALREADY_IN_FLIGHT;
        } catch (DataAccessException exception) {
            log.warn("Redis ticket-order idempotency in-flight gate failed open.", exception);
            return InflightClaimResult.FAIL_OPEN;
        }
    }

    public void release(String idempotencyKey) {
        try {
            stringRedisTemplate.delete(key(idempotencyKey));
        } catch (DataAccessException exception) {
            log.warn("Redis ticket-order idempotency in-flight release failed.", exception);
        }
    }

    private String key(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}
