package com.min.edu.payment.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "payment.outbox")
public class PaymentOutboxProperties {

    private boolean enabled = true;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration fixedDelay = Duration.ofSeconds(5);

    @Min(1)
    private int batchSize = 50;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration leaseDuration = Duration.ofSeconds(30);

    @NotNull
    @DurationMin(millis = 1)
    private Duration leaseRenewalInterval = Duration.ofSeconds(10);

    @Min(1)
    @Max(20)
    private int maxRetries = 5;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration initialBackoff = Duration.ofSeconds(5);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration maxBackoff = Duration.ofMinutes(5);

    @AssertTrue(message = "payment.outbox.lease-renewal-interval must be shorter than payment.outbox.lease-duration")
    public boolean isLeaseRenewalIntervalShorterThanLeaseDuration() {
        return leaseRenewalInterval.compareTo(leaseDuration) < 0;
    }
}
