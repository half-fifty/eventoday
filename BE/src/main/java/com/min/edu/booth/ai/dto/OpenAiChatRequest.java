package com.min.edu.booth.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// reasoningEffort는 null이면 아예 전송하지 않는다 — 일부 모델(OpenAI gpt-4o-mini 등)은
// 지원하지 않는 파라미터가 실려오면 요청 자체를 400으로 거부한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("reasoning_effort") String reasoningEffort) {

    public record Message(String role, String content) {
    }
}
