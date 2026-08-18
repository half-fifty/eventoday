package com.min.edu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiCopilotRequest(
        @NotBlank
        @Size(max = 2000)
        String question,

        @Size(max = 100)
        String orderNo) {
}
