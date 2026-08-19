package com.min.edu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiFailureExplanationRequest(
        @NotBlank
        @Size(max = 1000)
        String question) {
}
