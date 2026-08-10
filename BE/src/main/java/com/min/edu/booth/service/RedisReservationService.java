package com.min.edu.booth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
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
        // 1) 선점 키 생성
        String key = buildKey(boothId, slotId);
        String value = memberId.toString();

        // 2) SET NX (키가 없을 때만 set)
        // (1) 이미 다른 사용자가 선점했으면 false 반환
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofMinutes(RESERVATION_TTL_MINUTES));

        return result != null && result;
    }

    /**
     * 선점된 예약이 현재 사용자의 것인지 확인
     */
    public boolean isReservedByMember(Long boothId, Long slotId, Long memberId) {
        // 1) 선점 키 생성
        String key = buildKey(boothId, slotId);

        // 2) Redis에 저장된 값 조회
        String value = stringRedisTemplate.opsForValue().get(key);

        // 3) 값이 없거나 다르면 false
        if (value == null) {
            return false;  // 선점 없음
        }

        return value.equals(memberId.toString());
    }

    /**
     * 선점 해제 (Lua 스크립트로 원자적 compare-and-delete)
     *
     * @param boothId 부스 ID
     * @param slotId 슬롯 ID
     * @param memberId 회원 ID (소유자 검증용)
     */
    public void releaseSlot(Long boothId, Long slotId, Long memberId) {
        // 1) 선점 키 생성
        String key = buildKey(boothId, slotId);

        // 2) Lua 스크립트로 compare-and-delete
        // (1) Redis에 저장된 memberId와 전달받은 memberId를 비교
        // (2) 같으면 삭제, 다르면 삭제 안 함 (원자적 처리)
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else " +
                "return 0 " +
                "end";

        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);

        stringRedisTemplate.execute(
                script,
                Collections.singletonList(key),    // KEYS[1]
                memberId.toString()                 // ARGV[1]
        );
    }

    /**
     * 선점 유효성 확인
     */
    public boolean isSlotReserved(Long boothId, Long slotId) {
        // 1) 선점 키 생성
        String key = buildKey(boothId, slotId);

        // 2) 키 존재 여부 확인
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 선점 남은 시간 (초 단위)
     */
    public Long getReservationTTL(Long boothId, Long slotId) {
        // 1) 선점 키 생성
        String key = buildKey(boothId, slotId);

        // 2) TTL 조회
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 선점 키 생성
     */
    private String buildKey(Long boothId, Long slotId) {
        return RESERVATION_KEY_PREFIX + boothId + ":" + slotId;
    }
}