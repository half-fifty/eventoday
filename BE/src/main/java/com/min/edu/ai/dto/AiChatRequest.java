package com.min.edu.ai.dto;

public record AiChatRequest(
        String systemPrompt,
        String userPrompt) {
}
