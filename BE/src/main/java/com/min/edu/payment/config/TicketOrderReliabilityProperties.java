package com.min.edu.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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
    private Duration idempotencyProcessingTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration inflightTtl = Duration.ofSeconds(120);

    @NotNull
    private Admission admission = new Admission();

    @Getter
    @Setter
    public static class Admission {

        @Min(1)
        private int maxInFlightPerEvent = 50;

        @NotNull
        private Duration leaseTtl = Duration.ofSeconds(120);

        @Min(1)
        private long retryAfterSeconds = 1;
    }
}
