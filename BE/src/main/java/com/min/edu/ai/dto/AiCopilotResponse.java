package com.min.edu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiCopilotResponse(
        @NotBlank String answer,
        @NotNull AiCategory category,
        boolean needsHumanSupport) {
}
