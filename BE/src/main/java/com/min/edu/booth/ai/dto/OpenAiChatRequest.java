package com.min.edu.booth.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// reasoningEffort/maxTokens/maxCompletionTokens는 null이면 아예 전송하지 않는다 — 일부 모델은
// 지원하지 않는 파라미터가 실려오면 요청 자체를 400으로 거부한다.
// (예: o1/o3 계열은 max_tokens 대신 max_completion_tokens를 요구한다)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
        @JsonProperty("reasoning_effort") String reasoningEffort) {

    public record Message(String role, String content) {
    }
}
