package com.min.edu.ai.dto;

public record AiFailureExplanationResponse(
        String explanation,
        String recommendedAction,
        boolean needsHumanSupport,
        boolean aiGenerated,
        String reasonCode) {
}
