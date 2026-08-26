package com.min.edu.booth.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class GeminiVisionDtos {

    private GeminiVisionDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(List<RequestContent> contents, GenerationConfig generationConfig) {
    }

    public record RequestContent(List<RequestPart> parts) {
    }

    // text/inlineData 중 하나만 채워서 보내므로 나머지는 직렬화에서 제외한다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestPart(String text, InlineData inlineData) {
    }

    public record InlineData(String mimeType, String data) {
    }

    public record GenerationConfig(
            String responseMimeType,
            Object responseSchema,
            @JsonProperty("maxOutputTokens") Integer maxOutputTokens) {
    }

    public record Response(List<Candidate> candidates) {
    }

    public record Candidate(ResponseContent content, String finishReason) {
    }

    public record ResponseContent(List<ResponsePart> parts) {
    }

    public record ResponsePart(String text) {
    }

    // Gemini가 반환하는 원본 검출 결과. box_2d는 [y0, x0, y1, x1], 0~1000 정규화 좌표.
    public record RawDetection(String label, @JsonProperty("box_2d") List<Integer> box2d) {
    }
}
