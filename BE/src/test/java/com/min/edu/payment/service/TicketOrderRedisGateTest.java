package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.min.edu.payment.config.TicketOrderReliabilityProperties;

@ExtendWith(MockitoExtension.class)
class TicketOrderRedisGateTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private TicketOrderAdmissionGate admissionGate;
    private TicketOrderInflightDuplicateGate inflightDuplicateGate;

    @BeforeEach
    void setUp() {
        TicketOrderReliabilityProperties properties = new TicketOrderReliabilityProperties();
        admissionGate = new TicketOrderAdmissionGate(stringRedisTemplate, properties);
        inflightDuplicateGate = new TicketOrderInflightDuplicateGate(stringRedisTemplate, properties);
    }

    @Test
    void admissionTryAcquireFailsOpenOnRedisCommandTimeout() {
        given(stringRedisTemplate.execute(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .willThrow(new QueryTimeoutException("redis timeout"));

        assertThat(admissionGate.tryAcquire(1L, "key-1")).isEqualTo(AdmissionResult.FAIL_OPEN);
    }

    @Test
    void admissionReleaseIgnoresRedisCommandTimeout() {
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        doThrow(new QueryTimeoutException("redis timeout"))
            .when(zSetOperations).remove("ticket-order:admission:1", "key-1");

        assertThatNoException().isThrownBy(() -> admissionGate.release(1L, "key-1"));
    }

    @Test
    void inflightTryClaimFailsOpenOnRedisCommandTimeout() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .willThrow(new QueryTimeoutException("redis timeout"));

        assertThat(inflightDuplicateGate.tryClaim("key-1")).isEqualTo(InflightClaimResult.FAIL_OPEN);
    }

    @Test
    void inflightReleaseIgnoresRedisCommandTimeout() {
        doThrow(new QueryTimeoutException("redis timeout"))
            .when(stringRedisTemplate).delete("ticket-order:idempotency:key-1");

        assertThatNoException().isThrownBy(() -> inflightDuplicateGate.release("key-1"));
    }
}
