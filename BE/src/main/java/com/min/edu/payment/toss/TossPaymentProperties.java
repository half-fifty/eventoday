package com.min.edu.payment.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "toss-payments")
public class TossPaymentProperties {

    @NotBlank
    private String secretKey;

    @NotBlank
    private String baseUrl;

    private long connectTimeoutMs = 3000L;

    private long readTimeoutMs = 5000L;
}
