package com.min.edu.payment.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "payment.virtual-account-reconciliation")
public class VirtualAccountReconciliationProperties {

    private boolean enabled = true;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration fixedDelay = Duration.ofSeconds(60);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration ageThreshold = Duration.ofSeconds(60);

    @Min(1)
    private int batchSize = 50;
}
