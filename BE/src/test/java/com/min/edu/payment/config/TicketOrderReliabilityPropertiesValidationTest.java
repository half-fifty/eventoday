package com.min.edu.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class TicketOrderReliabilityPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void failsFastWhenAdmissionMaxInFlightPerEventIsZero() {
        assertInvalid("ticket-order.reliability.admission.max-in-flight-per-event=0");
    }

    @Test
    void failsFastWhenIdempotencyProcessingTtlIsZero() {
        assertInvalid("ticket-order.reliability.idempotency-processing-ttl=0s");
    }

    @Test
    void failsFastWhenIdempotencyProcessingTtlIsNegative() {
        assertInvalid("ticket-order.reliability.idempotency-processing-ttl=-1s");
    }

    @Test
    void failsFastWhenInflightTtlIsZero() {
        assertInvalid("ticket-order.reliability.inflight-ttl=0s");
    }

    @Test
    void failsFastWhenInflightTtlIsNegative() {
        assertInvalid("ticket-order.reliability.inflight-ttl=-1s");
    }

    @Test
    void failsFastWhenAdmissionLeaseTtlIsZero() {
        assertInvalid("ticket-order.reliability.admission.lease-ttl=0s");
    }

    @Test
    void failsFastWhenAdmissionLeaseTtlIsNegative() {
        assertInvalid("ticket-order.reliability.admission.lease-ttl=-1s");
    }

    private void assertInvalid(String property) {
        contextRunner
            .withPropertyValues(property)
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TicketOrderReliabilityProperties.class)
    static class PropertiesConfiguration {
    }
}
