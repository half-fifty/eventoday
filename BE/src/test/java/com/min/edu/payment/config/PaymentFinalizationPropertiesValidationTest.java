package com.min.edu.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class PaymentFinalizationPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void failsFastWhenConfirmInflightTtlIsZero() {
        assertInvalid("payment.confirm-inflight-ttl=0s");
    }

    @Test
    void failsFastWhenConfirmInflightTtlIsNegative() {
        assertInvalid("payment.confirm-inflight-ttl=-1s");
    }

    private void assertInvalid(String property) {
        contextRunner
            .withPropertyValues(property)
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentFinalizationProperties.class)
    static class PropertiesConfiguration {
    }
}
