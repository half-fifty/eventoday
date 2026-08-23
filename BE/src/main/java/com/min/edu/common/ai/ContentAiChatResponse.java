package com.min.edu.common.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** OpenAI Chat Completions 형식의 응답 본문 (필요한 필드만 매핑) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentAiChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {}
}
