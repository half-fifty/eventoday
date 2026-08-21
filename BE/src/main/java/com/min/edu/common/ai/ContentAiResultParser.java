package com.min.edu.common.ai;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.html.HtmlSanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 응답(JSON 문자열)을 화면에서 바로 쓸 수 있는 형태로 바꾼다.
 *
 * 사이트 공지와 행사 공지·자료가 같은 응답 형식을 쓰므로 파싱·정제를 여기 모았다.
 * 각 서비스는 권한 검증과 프롬프트 조립만 담당한다.
 */
@Slf4j
@Component
public class ContentAiResultParser {

    /** 제목 후보 최대 개수 — 화면에서 고르기 좋은 수준으로 제한한다 */
    public static final int MAX_TITLE_SUGGESTIONS = 5;

    /** 저장 시 제목 길이 제한(200자)에 미리 맞춰 보낸다 */
    private static final int MAX_TITLE_LENGTH = 200;

    /** 모델이 붙이는 ```json ... ``` 코드펜스를 걷어내기 위한 패턴 */
    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public ContentAiResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 파싱 결과. 동작에 따라 채워지는 필드가 다르다. */
    public record Result(String title, String content, List<String> titleSuggestions) {}

    /** 제목·본문을 돌려주는 동작(새 글 작성·다듬기·요약 등)의 응답을 파싱한다 */
    public Result parseContent(String rawResponse) {
        JsonNode parsed = parseJson(rawResponse);

        String content = HtmlSanitizer.sanitize(text(parsed, "content"));
        if (content == null) {
            log.warn("AI 응답 본문이 정제 후 비었습니다.");
            throw new BusinessException(GlobalErrorCode.CONTENT_AI_INVALID_RESPONSE);
        }
        return new Result(trimTitle(text(parsed, "title")), content, List.of());
    }

    /** 제목 후보만 돌려주는 동작의 응답을 파싱한다 */
    public Result parseTitleSuggestions(String rawResponse) {
        JsonNode parsed = parseJson(rawResponse);
        JsonNode node = parsed.get("titleSuggestions");

        List<String> titles = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode element : node) {
                // Jackson 3에서 asText()는 deprecated. 문자열이 아니면 예외 대신 null을 받도록 기본값을 넘긴다
                String title = trimTitle(element.asString(null));
                if (title != null && !titles.contains(title)) {
                    titles.add(title);
                }
                if (titles.size() >= MAX_TITLE_SUGGESTIONS) {
                    break;
                }
            }
        }
        if (titles.isEmpty()) {
            log.warn("AI 응답에서 제목 후보를 찾지 못했습니다.");
            throw new BusinessException(GlobalErrorCode.CONTENT_AI_INVALID_RESPONSE);
        }
        return new Result(null, null, titles);
    }

    /**
     * 모델 응답에서 JSON을 꺼낸다.
     * 지시를 해도 코드펜스를 붙이거나 앞뒤에 설명을 덧붙이는 경우가 있어 두 단계로 처리한다.
     */
    private JsonNode parseJson(String rawResponse) {
        String candidate = stripCodeFence(rawResponse);
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception first) {
            // 앞뒤에 설명이 붙은 경우 가장 바깥 중괄호 구간만 잘라 한 번 더 시도한다
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(candidate.substring(start, end + 1));
                } catch (Exception ignored) {
                    // 아래 공통 처리로 넘어간다
                }
            }
            // AI 응답에는 행사 정보나 작성자가 입력한 본문이 그대로 섞일 수 있어 운영 로그에는 길이만 남긴다.
            // 원인을 추적해야 할 때만 로그 레벨을 DEBUG로 올려서 확인한다.
            log.warn("AI 응답을 JSON으로 해석하지 못했습니다. responseLength={}",
                    rawResponse == null ? 0 : rawResponse.length());
            log.debug("해석하지 못한 AI 응답: {}", abbreviate(rawResponse));
            throw new BusinessException(GlobalErrorCode.CONTENT_AI_INVALID_RESPONSE, first);
        }
    }

    private String stripCodeFence(String rawResponse) {
        Matcher matcher = CODE_FENCE.matcher(rawResponse);
        return matcher.matches() ? matcher.group(1) : rawResponse;
    }

    private String text(JsonNode parsed, String field) {
        JsonNode node = parsed.get(field);
        // 모델이 문자열 대신 객체·배열을 넣어 보내도 예외로 죽지 않도록 기본값을 준다
        return node == null ? null : node.asString(null);
    }

    private String trimTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return null;
        }
        String title = rawTitle.trim();
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }
}
