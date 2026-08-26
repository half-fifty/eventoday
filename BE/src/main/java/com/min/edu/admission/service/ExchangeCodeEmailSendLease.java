package com.min.edu.admission.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExchangeCodeEmailSendLease {

    private static final String KEY_PREFIX = "exchange-code:email-send:";
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration ttl;

    public ExchangeCodeEmailSendLease(
            StringRedisTemplate stringRedisTemplate,
            @Value("${exchange-code.email-send-lease-ttl:30s}") Duration ttl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttl = ttl;
    }

    public ExchangeCodeEmailSendLeaseClaim tryClaim(Long requestId) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key(requestId), token, ttl);
            return Boolean.TRUE.equals(acquired)
                ? ExchangeCodeEmailSendLeaseClaim.acquired(token)
                : ExchangeCodeEmailSendLeaseClaim.alreadyInFlight();
        } catch (DataAccessException exception) {
            log.warn(
                "Redis exchange-code email send lease unavailable. requestId={}",
                requestId,
                exception
            );
            // Fail closed: without the lease store we cannot prevent concurrent SMTP sends.
            return ExchangeCodeEmailSendLeaseClaim.unavailable();
        }
    }

    public void release(Long requestId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key(requestId)), token);
        } catch (DataAccessException exception) {
            log.warn(
                "Redis exchange-code email send lease release failed. requestId={}",
                requestId,
                exception
            );
        }
    }

    private String key(Long requestId) {
        return KEY_PREFIX + requestId;
    }
}
