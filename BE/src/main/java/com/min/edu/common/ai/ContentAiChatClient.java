package com.min.edu.common.ai;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공지 AI 작성 보조용 LLM 호출 클라이언트.
 *
 * 부스 리뷰 요약(booth/ai/OpenContentAiChatClient)과 같은 방식이지만 설정을 공유하지 않는다.
 * 두 기능이 모델·키·토큰 한도를 각각 관리해야 해서 클라이언트를 분리했다.
 * (리뷰 요약은 300토큰·10초, 공지 생성은 2000토큰·45초로 요구치가 다르다)
 */
@Slf4j
@Component
public class ContentAiChatClient {

    // o1/o3 등 OpenAI 추론 모델은 max_tokens 대신 max_completion_tokens를 요구한다.
    private static final Pattern REASONING_MODEL_PATTERN =
            Pattern.compile("^o[0-9]([-.].*)?$", Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final int maxTokens;

    public ContentAiChatClient(
            @Value("${external.content-ai.base-url}") String baseUrl,
            @Value("${external.content-ai.api-key:}") String apiKey,
            @Value("${external.content-ai.model}") String model,
            @Value("${external.content-ai.reasoning-effort:}") String reasoningEffort,
            @Value("${external.content-ai.max-tokens:2500}") int maxTokens,
            @Value("${external.content-ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${external.content-ai.read-timeout:45s}") Duration readTimeout) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.maxTokens = maxTokens;
    }

    /** AI 기능을 쓸 수 있는 상태인지 (키 미설정이면 false) */
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    /**
     * 모델에 질의하고 응답 본문을 반환한다.
     *
     * @param systemPrompt 모델 지시문
     * @param userPrompt   관리자가 입력한 데이터. 지시문과 분리해 프롬프트 인젝션을 막는다.
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            log.warn("AI API key가 설정되지 않아 공지 AI 작성을 사용할 수 없습니다.");
            throw new BusinessException(GlobalErrorCode.CONTENT_AI_UNAVAILABLE);
        }

        boolean reasoningModel = REASONING_MODEL_PATTERN.matcher(model).matches();
        ContentAiChatRequest request = new ContentAiChatRequest(
                model,
                List.of(
                        new ContentAiChatRequest.Message("system", systemPrompt),
                        new ContentAiChatRequest.Message("user", userPrompt)
                ),
                reasoningModel ? null : maxTokens,
                reasoningModel ? maxTokens : null,
                reasoningEffort.isBlank() ? null : reasoningEffort
        );

        try {
            ContentAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(ContentAiChatResponse.class);

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("AI 응답에 사용할 수 있는 content가 없습니다. model={}", model);
                throw new BusinessException(GlobalErrorCode.CONTENT_AI_UNAVAILABLE);
            }
            return content.trim();
        } catch (RestClientException exception) {
            // 인증 실패·사용량 초과·타임아웃을 모두 여기서 받는다.
            // 원인별 메시지를 그대로 내보내면 키 상태 등이 노출될 수 있어 하나로 묶어 응답한다.
            log.warn("AI 호출에 실패했습니다. model={}, message={}", model, exception.getMessage());
            throw new BusinessException(GlobalErrorCode.CONTENT_AI_UNAVAILABLE, exception);
        }
    }

    private String extractContent(ContentAiChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ContentAiChatResponse.Choice choice = response.choices().get(0);
        return choice.message() == null ? null : choice.message().content();
    }
}
