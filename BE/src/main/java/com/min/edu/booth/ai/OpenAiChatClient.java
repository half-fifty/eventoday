package com.min.edu.booth.ai;

import com.min.edu.booth.ai.dto.OpenAiChatRequest;
import com.min.edu.booth.ai.dto.OpenAiChatResponse;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiChatClient {

    private static final int MAX_TOKENS = 300;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;

    public OpenAiChatClient(
            @Value("${external.openai.base-url}") String baseUrl,
            @Value("${external.openai.api-key:}") String apiKey,
            @Value("${external.openai.model}") String model,
            @Value("${external.openai.reasoning-effort:}") String reasoningEffort,
            @Value("${external.openai.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.openai.read-timeout:10s}") Duration readTimeout) {
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
    }

    // systemPrompt: 모델 지시문. userPrompt: 리뷰 등 사용자 유래 데이터 (프롬프트 인젝션 방지를 위해 분리)
    public String summarize(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            log.warn("OpenAI API key가 설정되지 않아 리뷰 요약을 생성하지 않습니다.");
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
        }

        OpenAiChatRequest request = new OpenAiChatRequest(
            model,
            List.of(
                new OpenAiChatRequest.Message("system", systemPrompt),
                new OpenAiChatRequest.Message("user", userPrompt)
            ),
            MAX_TOKENS,
            reasoningEffort.isBlank() ? null : reasoningEffort
        );

        try {
            OpenAiChatResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(OpenAiChatResponse.class);

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("OpenAI 응답에 사용할 수 있는 content가 없습니다. model={}", model);
                throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
            }
            return content.trim();
        } catch (RestClientException exception) {
            log.warn("OpenAI 요약 호출에 실패했습니다. model={}, message={}", model, exception.getMessage());
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE, exception);
        }
    }

    private String extractContent(OpenAiChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        OpenAiChatResponse.Message message = response.getChoices().get(0).getMessage();
        return message == null ? null : message.getContent();
    }
}
