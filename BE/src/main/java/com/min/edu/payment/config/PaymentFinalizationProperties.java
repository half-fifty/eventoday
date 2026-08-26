package com.min.edu.payment.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentFinalizationProperties {

    private long finalizationLockTimeoutMs = 5000L;

    private long webhookFinalizationLockTimeoutMs = 3000L;

    @NotNull
    @DurationMin(seconds = 0, inclusive = false)
    private Duration confirmInflightTtl = Duration.ofSeconds(30);
}
