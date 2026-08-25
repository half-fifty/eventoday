package com.min.edu.admission.service;

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

class ExchangeCodeEmailSendLeaseTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ExchangeCodeEmailSendLease lease;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        lease = new ExchangeCodeEmailSendLease(stringRedisTemplate, Duration.ofSeconds(30));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryClaimAcquiresWithOwnerTokenAndTtl() {
        when(valueOperations.setIfAbsent(
            eq("exchange-code:email-send:7"),
            any(),
            eq(Duration.ofSeconds(30))
        )).thenReturn(true);

        ExchangeCodeEmailSendLeaseClaim claim = lease.tryClaim(7L);

        assertThat(claim.result()).isEqualTo(ExchangeCodeEmailSendLeaseResult.ACQUIRED);
        assertThat(claim.token()).isNotBlank();
    }

    @Test
    void tryClaimReportsAlreadyInFlightWhenActiveLeaseExists() {
        when(valueOperations.setIfAbsent(
            eq("exchange-code:email-send:7"),
            any(),
            eq(Duration.ofSeconds(30))
        )).thenReturn(false);

        assertThat(lease.tryClaim(7L).result())
            .isEqualTo(ExchangeCodeEmailSendLeaseResult.ALREADY_IN_FLIGHT);
    }

    @Test
    void tryClaimAcquiresWhenRedisTtlHasExpiredThePreviousLease() {
        when(valueOperations.setIfAbsent(
            eq("exchange-code:email-send:7"),
            any(),
            eq(Duration.ofSeconds(30))
        )).thenReturn(false, true);

        assertThat(lease.tryClaim(7L).result())
            .isEqualTo(ExchangeCodeEmailSendLeaseResult.ALREADY_IN_FLIGHT);
        assertThat(lease.tryClaim(7L).result())
            .isEqualTo(ExchangeCodeEmailSendLeaseResult.ACQUIRED);
    }

    @Test
    void tryClaimFailsClosedWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(
            eq("exchange-code:email-send:7"),
            any(),
            eq(Duration.ofSeconds(30))
        )).thenThrow(new QueryTimeoutException("redis timeout"));

        assertThat(lease.tryClaim(7L).result())
            .isEqualTo(ExchangeCodeEmailSendLeaseResult.UNAVAILABLE);
    }

    @Test
    void releaseUsesCompareAndDeleteScriptWithOwnerToken() {
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-A")
        )).thenReturn(1L);

        lease.release(7L, "token-A");

        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-A")
        );
        verify(stringRedisTemplate, never()).delete("exchange-code:email-send:7");
    }

    @Test
    void staleOwnerReleaseDoesNotDeleteCurrentLease() {
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-A")
        )).thenReturn(0L);
        when(stringRedisTemplate.execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-B")
        )).thenReturn(1L);

        lease.release(7L, "token-A");
        lease.release(7L, "token-B");

        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-A")
        );
        verify(stringRedisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("exchange-code:email-send:7")),
            eq("token-B")
        );
        verify(stringRedisTemplate, never()).delete("exchange-code:email-send:7");
    }

    @Test
    void releaseIgnoresRedisFailure() {
        doThrow(new QueryTimeoutException("redis timeout"))
            .when(stringRedisTemplate)
            .execute(any(RedisScript.class), eq(List.of("exchange-code:email-send:7")), eq("token-A"));

        assertThatNoException().isThrownBy(() -> lease.release(7L, "token-A"));
    }
}
