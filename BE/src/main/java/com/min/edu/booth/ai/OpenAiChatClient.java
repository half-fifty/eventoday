package com.min.edu.booth.ai;

import com.min.edu.booth.ai.dto.OpenAiChatRequest;
import com.min.edu.booth.ai.dto.OpenAiChatResponse;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiChatClient {

    private static final int MAX_TOKENS = 300;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiChatClient(
            @Value("${external.openai.base-url}") String baseUrl,
            @Value("${external.openai.api-key:}") String apiKey,
            @Value("${external.openai.model}") String model,
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
    }

    public String summarize(String prompt) {
        if (apiKey.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
        }

        OpenAiChatRequest request = new OpenAiChatRequest(
            model,
            List.of(new OpenAiChatRequest.Message("user", prompt)),
            MAX_TOKENS
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
                throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
            }
            return content.trim();
        } catch (RestClientException exception) {
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
