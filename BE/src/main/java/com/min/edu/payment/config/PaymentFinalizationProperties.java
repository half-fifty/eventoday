package com.min.edu.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentFinalizationProperties {

    private long finalizationLockTimeoutMs = 5000L;
}
