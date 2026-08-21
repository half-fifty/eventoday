package com.min.edu.payment.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.min.edu.payment.config.PaymentFinalizationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmInflightDuplicateGate {

    private static final String KEY_PREFIX = "payment:confirm:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentFinalizationProperties properties;

    public InflightClaimResult tryClaim(String orderNo) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key(orderNo), "1", properties.getConfirmInflightTtl());
            return Boolean.TRUE.equals(acquired)
                ? InflightClaimResult.ACQUIRED
                : InflightClaimResult.ALREADY_IN_FLIGHT;
        } catch (DataAccessException exception) {
            log.warn("Redis payment confirm in-flight gate failed open: orderNo={}", orderNo, exception);
            return InflightClaimResult.FAIL_OPEN;
        }
    }

    public void release(String orderNo) {
        try {
            stringRedisTemplate.delete(key(orderNo));
        } catch (DataAccessException exception) {
            log.warn("Redis payment confirm in-flight release failed: orderNo={}", orderNo, exception);
        }
    }

    private String key(String orderNo) {
        return KEY_PREFIX + orderNo;
    }
}
