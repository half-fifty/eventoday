package com.min.edu.advertisement.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.advertisement.dto.AdvertisementDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class AdvertisementCopySuggestionService {
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);
    private static final Set<String> EVIDENCE_REQUIRED_TERMS = Set.of(
            "대표", "최대", "최고", "유일", "무료", "할인", "혜택", "체험",
            "상담", "시연", "세미나", "프로그램", "네트워킹", "보장");
    private final EventRepository eventRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final RestClient groqClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final AdvertisementKnowledgeRetriever knowledgeRetriever;

    public AdvertisementCopySuggestionService(
            EventRepository eventRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            AdvertisementKnowledgeRetriever knowledgeRetriever,
            @Value("${external.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${external.groq.api-key:}") String apiKey,
            @Value("${external.groq.model:openai/gpt-oss-120b}") String model,
            @Value("${external.groq.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.groq.read-timeout:15s}") Duration readTimeout) {
        this.eventRepository = eventRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.apiKey = apiKey;
        this.model = model;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.groqClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public AdvertisementDtos.CopySuggestionResponse suggest(
            AdvertisementDtos.CopySuggestionRequest request, AuthenticatedMemberDto actor) {
        requireManager(request.organizationId(), actor);
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        if (!event.getOrganizerOrganizationId().equals(request.organizationId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        AdvertisementKnowledgeRetriever.RetrievalResult retrieval =
                knowledgeRetriever.retrieve(event, request.draft(), request.tone());
        if (apiKey.isBlank()) {
            return new AdvertisementDtos.CopySuggestionResponse(List.of(), retrieval.sources(),
                    "Groq API 키가 설정되지 않았습니다. GROQ_API_KEY를 확인해 주세요.");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.5,
                    "reasoning_effort", "low",
                    "max_completion_tokens", 900,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", userPrompt(request, retrieval))));
            String responseBody = groqClient.post().uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve().body(String.class);
            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
            String content = response == null ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            GroqPayload payload = objectMapper.readValue(content, GroqPayload.class);
            List<GroqSuggestion> generated = payload.suggestions() == null ? List.of() : payload.suggestions();
            List<AdvertisementDtos.CopySuggestion> suggestions = generated.stream()
                    .filter(item -> item.copy() != null && !item.copy().isBlank())
                    .map(item -> verifiedSuggestion(item, retrieval))
                    .filter(java.util.Objects::nonNull)
                    .limit(3)
                    .toList();
            if (suggestions.isEmpty()) suggestions = List.of(safeFallback(event));
            return new AdvertisementDtos.CopySuggestionResponse(
                    suggestions, retrieval.sources(), suggestions.isEmpty() ? "추천 문구를 생성하지 못했습니다." : null);
        } catch (RestClientException | JsonProcessingException | NullPointerException exception) {
            log.warn("Groq 광고 문구 추천 호출 실패. model={}, message={}", model, exception.getMessage());
            return new AdvertisementDtos.CopySuggestionResponse(List.of(), retrieval.sources(),
                    "AI 추천을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void requireManager(Long organizationId, AuthenticatedMemberDto actor) {
        if (actor == null) throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        if (!organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String systemPrompt() {
        return "당신은 행사 플랫폼의 광고 카피 작성 보조자입니다. 제공된 행사 사실과 정책만 사용하세요. "
                + "확인되지 않은 할인, 수치, 프로그램을 만들지 마세요. 반드시 JSON 객체로만 답하고 "
                + "형식은 {\"suggestions\":[{\"copy\":\"\",\"rationale\":\"\",\"warnings\":[\"\"]}]} 입니다. "
                + "서로 다른 문구 3개를 한국어로 생성하고 각 copy는 300자 이내로 작성하세요.";
    }

    private String userPrompt(AdvertisementDtos.CopySuggestionRequest request,
            AdvertisementKnowledgeRetriever.RetrievalResult retrieval) {
        return "[RAG 검색 근거]\n" + retrieval.asPromptContext()
                + "\n위 검색 근거에 없는 사실은 절대 추가하지 마세요."
                + "\n[사용자 선택 문체]\n" + safe(request.tone())
                + "\n[사용자 초안]\n" + safe(request.draft());
    }

    private String safe(Object value) { return value == null ? "정보 없음" : value.toString(); }

    private AdvertisementDtos.CopySuggestion verifiedSuggestion(
            GroqSuggestion item, AdvertisementKnowledgeRetriever.RetrievalResult retrieval) {
        String copy = cleanModelText(item.copy());
        String evidence = retrieval.asEventFactContext().toLowerCase();
        List<String> unsupported = EVIDENCE_REQUIRED_TERMS.stream()
                .filter(copy::contains)
                .filter(term -> !evidence.contains(term))
                .sorted()
                .toList();
        if (!unsupported.isEmpty()) {
            log.info("근거 없는 광고 표현을 제외했습니다. terms={}", unsupported);
            return null;
        }
        if (copy.length() > 300) copy = copy.substring(0, 300);
        return new AdvertisementDtos.CopySuggestion(copy, cleanModelText(item.rationale()),
                item.warnings() == null ? List.of() : item.warnings().stream()
                        .map(this::cleanModelText).filter(value -> !value.isBlank()).toList());
    }

    private AdvertisementDtos.CopySuggestion safeFallback(Event event) {
        StringBuilder copy = new StringBuilder(safe(event.getName()));
        if (event.getStartAt() != null && event.getEndAt() != null) {
            copy.append(" · ").append(event.getStartAt().toLocalDate())
                    .append("~").append(event.getEndAt().toLocalDate());
        }
        if (event.getVenueName() != null && !event.getVenueName().isBlank()) {
            copy.append(" · ").append(event.getVenueName());
        }
        copy.append("에서 만나요.");
        return new AdvertisementDtos.CopySuggestion(copy.toString(),
                "검증된 행사명·일정·장소만 사용한 안전 문구입니다.", List.of());
    }

    private String cleanModelText(String value) {
        if (value == null) return "";
        return value.replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replace("**", "").replace("__", "")
                .replace("`", "").replace("\\~", "~").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqPayload(List<GroqSuggestion> suggestions) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqSuggestion(String copy, String rationale, List<String> warnings) {}
}
