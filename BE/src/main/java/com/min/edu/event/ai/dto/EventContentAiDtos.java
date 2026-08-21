package com.min.edu.event.ai.dto;

import com.min.edu.common.ai.ContentAiAction;
import com.min.edu.common.ai.ContentAiTone;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class EventContentAiDtos {

    private EventContentAiDtos() {}

    /**
     * 행사 공지·자료 AI 작성 보조 요청.
     *
     * 사이트 공지와 달리 contentType·audience·resourceType을 함께 받는다.
     * 공지와 자료는 글의 성격이 다르고, 공개 대상에 따라 안내할 내용도 달라져
     * 이 값들이 결과 품질을 크게 좌우한다.
     */
    public record GenerateRequest(
            @NotNull ContentAiAction action,
            @NotNull EventContentType contentType,
            EventContentAudience audience,
            @Size(max = 30) String resourceType,
            ContentAiTone tone,
            @Size(max = 2000) String prompt,
            @Size(max = 200) String title,
            @Size(max = 20000) String content) {}

    /** 응답 구조는 사이트 공지와 동일하다. 프론트엔드가 같은 모달을 쓴다. */
    public record GenerateResponse(
            String title,
            String content,
            List<String> titleSuggestions) {

        public static GenerateResponse ofContent(String title, String content) {
            return new GenerateResponse(title, content, List.of());
        }

        public static GenerateResponse ofTitles(List<String> titleSuggestions) {
            return new GenerateResponse(null, null, titleSuggestions);
        }
    }
}
