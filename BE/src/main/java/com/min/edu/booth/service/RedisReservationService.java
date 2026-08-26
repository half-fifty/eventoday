package com.min.edu.booth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisReservationService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RESERVATION_KEY_PREFIX = "reservation:booth:";
    private static final long RESERVATION_TTL_MINUTES = 5;  // 5분 선점

    /**
     * 같은 회원이 같은 슬롯에 대해 (더블클릭 등으로) 요청을 중복으로 밀어넣는 것을 막는 용도의
     * 짧은 선점 락. 키에 memberId까지 포함한다 — boothId:slotId만으로 키를 잡으면 정원이 남아있는
     * 슬롯에서도 다른 회원의 요청이 서로를 막아버리는 문제가 있었다(실제 정원 검증은 이미
     * BoothReservationSlot 행에 건 비관적 락이 정확히 처리하고 있어서, 이 Redis 락은 "같은 회원의
     * 중복 요청 방지"로만 좁혀도 충분하다).
     * KEY: reservation:booth:{boothId}:{slotId}:{memberId}
     * TTL: 5분
     *
     * @return 선점 성공 여부
     */
    public boolean reserveSlot(Long boothId, Long slotId, Long memberId) {
        String key = buildKey(boothId, slotId, memberId);

        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(RESERVATION_TTL_MINUTES));

        return result != null && result;
    }

    /**
     * 선점 해제
     */
    public void releaseSlot(Long boothId, Long slotId, Long memberId) {
        String key = buildKey(boothId, slotId, memberId);
        stringRedisTemplate.delete(key);
    }

    private String buildKey(Long boothId, Long slotId, Long memberId) {
        return RESERVATION_KEY_PREFIX + boothId + ":" + slotId + ":" + memberId;
    }
}