package com.min.edu.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "ticket-order.reliability")
public class TicketOrderReliabilityProperties {

    @NotNull
    @DurationMin(seconds = 0, inclusive = false)
    private Duration idempotencyProcessingTtl = Duration.ofMinutes(15);

    @NotNull
    @DurationMin(seconds = 0, inclusive = false)
    private Duration inflightTtl = Duration.ofSeconds(120);

    @Valid
    @NotNull
    private Admission admission = new Admission();

    @Getter
    @Setter
    public static class Admission {

        @Min(1)
        private int maxInFlightPerEvent = 50;

        @NotNull
        @DurationMin(seconds = 0, inclusive = false)
        private Duration leaseTtl = Duration.ofSeconds(120);

        @Min(1)
        private long retryAfterSeconds = 1;
    }
}
