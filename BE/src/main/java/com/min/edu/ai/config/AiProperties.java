package com.min.edu.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.copilot")
public record AiProperties(
        @NotBlank String provider,
        String apiKey,
        @NotBlank String model,
        Duration connectTimeout,
        Duration readTimeout,
        @Min(1) int maxOutputTokens) {
}
