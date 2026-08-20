package com.min.edu.payment.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.min.edu.payment.config.TicketOrderReliabilityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOrderAdmissionGate {

    private static final String KEY_PREFIX = "ticket-order:admission:";
    private static final RedisScript<Long> ADMIT_SCRIPT = RedisScript.of("""
        redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])
        local current = redis.call('zcard', KEYS[1])
        if current >= tonumber(ARGV[3]) then
          return 0
        end
        redis.call('zadd', KEYS[1], ARGV[2], ARGV[4])
        redis.call('pexpire', KEYS[1], ARGV[5])
        return 1
        """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketOrderReliabilityProperties properties;

    public AdmissionResult tryAcquire(Long eventId, String idempotencyKey) {
        long nowMillis = Instant.now().toEpochMilli();
        long leaseMillis = properties.getAdmission().getLeaseTtl().toMillis();
        long expiresAtMillis = nowMillis + leaseMillis;

        try {
            Long result = stringRedisTemplate.execute(
                ADMIT_SCRIPT,
                List.of(key(eventId)),
                Long.toString(nowMillis),
                Long.toString(expiresAtMillis),
                Integer.toString(properties.getAdmission().getMaxInFlightPerEvent()),
                idempotencyKey,
                Long.toString(leaseMillis * 2)
            );
            return Long.valueOf(1L).equals(result)
                ? AdmissionResult.ACQUIRED
                : AdmissionResult.REJECTED;
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            log.warn("Redis ticket-order admission gate failed open: eventId={}", eventId, exception);
            return AdmissionResult.FAIL_OPEN;
        }
    }

    public void release(Long eventId, String idempotencyKey) {
        try {
            stringRedisTemplate.opsForZSet().remove(key(eventId), idempotencyKey);
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            log.warn("Redis ticket-order admission release failed: eventId={}", eventId, exception);
        }
    }

    private String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
