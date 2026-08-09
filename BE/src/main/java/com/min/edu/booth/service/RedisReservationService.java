package com.min.edu.booth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisReservationService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RESERVATION_KEY_PREFIX = "reservation:booth:";
    private static final long RESERVATION_TTL_MINUTES = 5;  // 5분 선점

    /**
     * Redis에 예약 임시 선점
     * KEY: reservation:booth:{boothId}:{slotId}
     * VALUE: {memberId}
     * TTL: 5분
     *
     * @return 선점 성공 여부
     */
    public boolean reserveSlot(Long boothId, Long slotId, Long memberId) {
        String key = buildKey(boothId, slotId);
        String value = memberId.toString();

        // SET NX (키가 없을 때만 set)
        // 이미 다른 사용자가 선점했으면 false 반환
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofMinutes(RESERVATION_TTL_MINUTES));

        return result != null && result;
    }

    /**
     * 선점된 예약이 현재 사용자의 것인지 확인
     */
    public boolean isReservedByMember(Long boothId, Long slotId, Long memberId) {
        String key = buildKey(boothId, slotId);
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value == null) {
            return false;  // 선점 없음
        }

        return value.equals(memberId.toString());
    }

    /**
     * 선점 해제 (예약 확정 또는 취소 시)
     */
    public void releaseSlot(Long boothId, Long slotId) {
        String key = buildKey(boothId, slotId);
        stringRedisTemplate.delete(key);
    }

    /**
     * 선점 유효성 확인
     */
    public boolean isSlotReserved(Long boothId, Long slotId) {
        String key = buildKey(boothId, slotId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 선점 남은 시간 (초 단위)
     */
    public Long getReservationTTL(Long boothId, Long slotId) {
        String key = buildKey(boothId, slotId);
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    private String buildKey(Long boothId, Long slotId) {
        return RESERVATION_KEY_PREFIX + boothId + ":" + slotId;
    }
}