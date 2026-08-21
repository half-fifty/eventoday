package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.min.edu.payment.config.PaymentFinalizationProperties;

class PaymentConfirmInflightDuplicateGateTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PaymentConfirmInflightDuplicateGate gate;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        PaymentFinalizationProperties properties = new PaymentFinalizationProperties();
        properties.setConfirmInflightTtl(Duration.ofSeconds(30));
        gate = new PaymentConfirmInflightDuplicateGate(stringRedisTemplate, properties);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryClaimAcquiresWhenRedisSetIfAbsentSucceeds() {
        when(valueOperations.setIfAbsent(eq("payment:confirm:ORDER-1"), any(), eq(Duration.ofSeconds(30))))
            .thenReturn(true);

        PaymentConfirmInflightClaim claim = gate.tryClaim("ORDER-1");

        assertThat(claim.result()).isEqualTo(InflightClaimResult.ACQUIRED);
        assertThat(claim.token()).isNotBlank();
    }

    @Test
    void tryClaimReportsAlreadyInFlightWhenRedisSetIfAbsentFails() {
        when(valueOperations.setIfAbsent(eq("payment:confirm:ORDER-1"), any(), eq(Duration.ofSeconds(30))))
            .thenReturn(false);

        assertThat(gate.tryClaim("ORDER-1").result())
            .isEqualTo(InflightClaimResult.ALREADY_IN_FLIGHT);
    }

    @Test
    void tryClaimFailsOpenOnRedisCommandTimeout() {
        when(valueOperations.setIfAbsent(eq("payment:confirm:ORDER-1"), any(), eq(Duration.ofSeconds(30))))
            .thenThrow(new QueryTimeoutException("redis timeout"));

        assertThat(gate.tryClaim("ORDER-1").result()).isEqualTo(InflightClaimResult.FAIL_OPEN);
    }

    @Test
    void releaseUsesCompareAndDeleteScriptWithOwnerToken() {
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-A")
        )).thenReturn(1L);

        gate.release("ORDER-1", "token-A");

        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-A")
        );
        verify(stringRedisTemplate, never()).delete("payment:confirm:ORDER-1");
    }

    @Test
    void staleOwnerReleaseDoesNotDeleteCurrentLease() {
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-A")
        )).thenReturn(0L);
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-B")
        )).thenReturn(1L);

        gate.release("ORDER-1", "token-A");
        gate.release("ORDER-1", "token-B");

        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-A")
        );
        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("payment:confirm:ORDER-1")),
            eq("token-B")
        );
        verify(stringRedisTemplate, never()).delete("payment:confirm:ORDER-1");
    }

    @Test
    void releaseIgnoresRedisCommandTimeout() {
        doThrow(new QueryTimeoutException("redis timeout"))
            .when(stringRedisTemplate)
            .execute(any(RedisScript.class), eq(List.of("payment:confirm:ORDER-1")), eq("token-A"));

        assertThatNoException().isThrownBy(() -> gate.release("ORDER-1", "token-A"));
    }
}
