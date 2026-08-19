package com.min.edu.ai.rag;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.rag")
public record RagProperties(
        boolean enabled,
        String apiKey,
        @NotBlank String embeddingModel,
        @Min(1) int embeddingDimensions,
        @Min(1) int topK,
        @DefaultValue("3") @Min(1) int copilotTopK,
        @NotBlank String policyLocation,
        @NotBlank String vectorTable,
        boolean ingestionEnabled) {
}
