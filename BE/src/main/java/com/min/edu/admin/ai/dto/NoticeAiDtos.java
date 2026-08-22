package com.min.edu.admin.ai.dto;

import com.min.edu.common.ai.ContentAiAction;
import com.min.edu.common.ai.ContentAiTone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class NoticeAiDtos {

    private NoticeAiDtos() {}

    /**
     * AI 작성 보조 요청.
     *
     * prompt·title·content 길이를 제한하는 이유는 두 가지다.
     * 토큰 사용량이 요청 크기에 비례해 비용이 늘고, 지나치게 긴 입력은 모델이 지시를 놓친다.
     */
    public record GenerateRequest(
            @NotNull ContentAiAction action,
            ContentAiTone tone,
            @Size(max = 2000) String prompt,
            @Size(max = 200) String title,
            @Size(max = 20000) String content) {}

    /**
     * AI 작성 결과.
     *
     * 동작에 따라 채워지는 필드가 다르다.
     * - GENERATE: title + content
     * - TITLE_SUGGEST: titleSuggestions
     * - 그 외: content
     *
     * content는 서버에서 HtmlSanitizer로 정제한 HTML이라 그대로 에디터에 넣어도 된다.
     */
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
