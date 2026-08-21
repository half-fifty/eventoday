package com.min.edu.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class PaymentOutboxPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultPropertiesAreValid() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsFastWhenMaxRetriesIsZero() {
        assertInvalid("payment.outbox.max-retries=0");
    }

    @Test
    void failsFastWhenMaxRetriesIsTooLarge() {
        assertInvalid("payment.outbox.max-retries=21");
    }

    @Test
    void failsFastWhenLeaseRenewalIntervalIsNotShorterThanLeaseDuration() {
        assertInvalid(
            "payment.outbox.lease-duration=30s",
            "payment.outbox.lease-renewal-interval=30s"
        );
    }

    private void assertInvalid(String... properties) {
        contextRunner
            .withPropertyValues(properties)
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentOutboxProperties.class)
    static class PropertiesConfiguration {
    }
}
