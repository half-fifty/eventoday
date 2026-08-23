package com.min.edu.common.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenAI Chat Completions 형식의 요청 본문.
 * Gemini의 OpenAI 호환 엔드포인트도 같은 포맷을 사용한다.
 *
 * null인 필드는 아예 전송하지 않는다 — 지원하지 않는 파라미터가 실려오면
 * 요청 자체를 400으로 거부하는 모델이 있다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
        @JsonProperty("reasoning_effort") String reasoningEffort) {

    public record Message(String role, String content) {}
}
