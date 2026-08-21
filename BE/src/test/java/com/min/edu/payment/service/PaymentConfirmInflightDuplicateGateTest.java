package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
        when(valueOperations.setIfAbsent("payment:confirm:ORDER-1", "1", Duration.ofSeconds(30)))
            .thenReturn(true);

        assertThat(gate.tryClaim("ORDER-1")).isEqualTo(InflightClaimResult.ACQUIRED);
    }

    @Test
    void tryClaimReportsAlreadyInFlightWhenRedisSetIfAbsentFails() {
        when(valueOperations.setIfAbsent("payment:confirm:ORDER-1", "1", Duration.ofSeconds(30)))
            .thenReturn(false);

        assertThat(gate.tryClaim("ORDER-1"))
            .isEqualTo(InflightClaimResult.ALREADY_IN_FLIGHT);
    }

    @Test
    void tryClaimFailsOpenOnRedisCommandTimeout() {
        when(valueOperations.setIfAbsent("payment:confirm:ORDER-1", "1", Duration.ofSeconds(30)))
            .thenThrow(new QueryTimeoutException("redis timeout"));

        assertThat(gate.tryClaim("ORDER-1")).isEqualTo(InflightClaimResult.FAIL_OPEN);
    }

    @Test
    void releaseIgnoresRedisCommandTimeout() {
        doThrow(new QueryTimeoutException("redis timeout"))
            .when(stringRedisTemplate)
            .delete("payment:confirm:ORDER-1");

        assertThatNoException().isThrownBy(() -> gate.release("ORDER-1"));
        verify(stringRedisTemplate).delete("payment:confirm:ORDER-1");
    }
}
