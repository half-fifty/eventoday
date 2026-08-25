package com.min.edu.event.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.dto.PosterExtractionDto;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.net.http.HttpClient;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class PosterVisionExtractionService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);
    private static final Semaphore ANALYSIS_SLOTS = new Semaphore(2, true);
    private static final Set<String> ALLOWED_CATEGORY_CODES = Set.of(
            "AGRI_FOOD", "ENERGY_ENVIRONMENT", "TEXTILE_FASHION_JEWELRY",
            "METAL_MACHINERY_EQUIPMENT", "ELECTRIC_ELECTRONICS_ICT_BROADCAST",
            "HEALTH_MEDICAL_OPTICS_PRECISION", "CONSTRUCTION_ARCHITECTURE_INTERIOR",
            "TRANSPORT_SERVICE", "HOUSEHOLD_GIFTS", "BEAUTY_COSMETICS",
            "FINANCE_REAL_ESTATE_PROFESSIONAL", "PUBLIC_DEFENSE", "EDUCATION",
            "PREGNANCY_BIRTH_CHILDCARE", "WEDDING", "CULTURE_ART",
            "LEISURE_TOURISM_SPORTS");

    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final RestClient groqClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public PosterVisionExtractionService(
            EventOrganizationMemberRepository organizationMemberRepository,
            @Value("${external.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${external.groq.api-key:}") String apiKey,
            @Value("${external.groq.vision-model:qwen/qwen3.6-27b}") String model,
            @Value("${external.groq.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.groq.vision-read-timeout:30s}") Duration readTimeout) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.apiKey = apiKey;
        this.model = model;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.groqClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public PosterExtractionDto extract(Long organizationId, MultipartFile image, AuthenticatedMemberDto actor) {
        requireManager(organizationId, actor);
        byte[] imageBytes = readAndValidate(image);
        String mediaType = detectMediaType(imageBytes);
        if (apiKey.isBlank()) return unavailable("Groq API 키가 설정되지 않았습니다.");
        if (!ANALYSIS_SLOTS.tryAcquire()) return unavailable("포스터 분석 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        try {
            String dataUrl = "data:" + mediaType + ";base64,"
                    + Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", prompt()),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
            String raw = groqClient.post().uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return sanitize(objectMapper.readValue(content, PosterExtractionDto.class));
        } catch (Exception exception) {
            log.warn("포스터 AI 분석에 실패했습니다. model={}, errorType={}",
                    model, exception.getClass().getSimpleName());
            return unavailable("포스터를 자동 분석하지 못했습니다. 내용을 직접 입력해 주세요.");
        } finally {
            ANALYSIS_SLOTS.release();
        }
    }

    private PosterExtractionDto unavailable(String warning) {
        return new PosterExtractionDto(null, null, null, null, null, null, null,
                null, List.of(), null, null, List.of(), List.of(warning));
    }

    static PosterExtractionDto sanitize(PosterExtractionDto result) {
        List<String> categoryCodes = result.categoryCodes() == null
                ? List.of()
                : new LinkedHashSet<>(result.categoryCodes()).stream()
                        .filter(ALLOWED_CATEGORY_CODES::contains)
                        .limit(5)
                        .toList();
        return new PosterExtractionDto(result.eventName(), result.startDate(), result.endDate(),
                result.venueName(), result.hall(), result.officialWebsiteUrl(), result.organizer(),
                result.operator(), result.sponsors() == null ? List.of() : result.sponsors(),
                result.summary(), result.rawVisibleText(), categoryCodes,
                result.warnings() == null ? List.of() : result.warnings());
    }

    private void requireManager(Long organizationId, AuthenticatedMemberDto actor) {
        if (actor == null) throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        if (!organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private byte[] readAndValidate(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        try {
            byte[] bytes = image.getBytes();
            detectMediaType(bytes);
            return bytes;
        } catch (IOException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    static String detectMediaType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
    }

    private String prompt() {
        return "행사 포스터에서 눈으로 확인되는 사실만 추출하세요. 추측하거나 빈 값을 만들지 마세요. "
                + "날짜는 연도가 보일 때만 yyyy-MM-dd로 변환하고, 종료 연도가 없으면 시작 연도를 사용하세요. "
                + "요일과 달력이 충돌하면 포스터에 인쇄된 날짜를 사용하고 warnings에 기록하세요. "
                + "포스터의 행사명, 소개 문구와 전시 내용을 근거로 다음 코드 중 관련도가 높은 전시품목을 최대 5개 추천해 categoryCodes 배열에 넣으세요: "
                + "AGRI_FOOD, ENERGY_ENVIRONMENT, TEXTILE_FASHION_JEWELRY, METAL_MACHINERY_EQUIPMENT, "
                + "ELECTRIC_ELECTRONICS_ICT_BROADCAST, HEALTH_MEDICAL_OPTICS_PRECISION, CONSTRUCTION_ARCHITECTURE_INTERIOR, "
                + "TRANSPORT_SERVICE, HOUSEHOLD_GIFTS, BEAUTY_COSMETICS, FINANCE_REAL_ESTATE_PROFESSIONAL, PUBLIC_DEFENSE, "
                + "EDUCATION, PREGNANCY_BIRTH_CHILDCARE, WEDDING, CULTURE_ART, LEISURE_TOURISM_SPORTS. "
                + "반드시 JSON 객체로만 답하세요. 키는 eventName,startDate,endDate,venueName,hall,officialWebsiteUrl,"
                + "organizer,operator,sponsors,summary,rawVisibleText,categoryCodes,warnings이며 문자열을 알 수 없으면 null, 배열은 []로 반환하세요.";
    }
}
