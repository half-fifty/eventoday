package com.min.edu.booth.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens) {

    public record Message(String role, String content) {
    }
}
