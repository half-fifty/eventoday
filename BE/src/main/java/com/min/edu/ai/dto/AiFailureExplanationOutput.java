package com.min.edu.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiFailureExplanationOutput(
        @NotBlank String explanation,
        @NotBlank String recommendedAction,
        boolean needsHumanSupport) {
}
