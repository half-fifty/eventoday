package com.min.edu.booth.ai;

import com.min.edu.booth.ai.dto.DetectedBoothBox;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.Candidate;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.GenerationConfig;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.InlineData;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.RawDetection;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.Request;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.RequestContent;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.RequestPart;
import com.min.edu.booth.ai.dto.GeminiVisionDtos.Response;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// 평면도 이미지에서 부스로 보이는 사각형 영역을 검출해 좌표 제안 목록으로 변환한다.
// 실측(2026-08-12): gemini-3.1-flash-lite가 gemini-3.5-flash-lite보다 이 작업에서 더 정밀했고,
// responseSchema 없이 프롬프트만으로 형식을 지시하면 밀집한 도면에서 JSON이 깨지는 현상이
// 재현됐다 - 그래서 responseSchema로 출력 생성 자체를 강제한다.
@Slf4j
@Component
public class GeminiVisionClient {

    // 도면 하나에 부스가 수백 개일 수 있어, 응답이 잘려 파싱 자체가 실패하는 것을 막기 위한 여유값.
    private static final int MAX_OUTPUT_TOKENS = 8192;

    private static final Object RESPONSE_SCHEMA = JsonMapper.builder().build().readValue("""
        {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "label": { "type": "STRING" },
              "box_2d": { "type": "ARRAY", "items": { "type": "INTEGER" } }
            },
            "required": ["label", "box_2d"]
          }
        }
        """, Object.class);

    private static final String DETECTION_PROMPT = """
        This is an exhibition floorplan. Detect every individual booth cell (a labeled \
        rectangular/box area representing one exhibitor booth, e.g. codes like A01, C12, \
        D200, AC03, S104). Do NOT include section headers, legends, category labels, hall \
        names, or dimension numbers on connector lines.""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final String apiKey;
    private final String model;

    public GeminiVisionClient(
            @Value("${external.gemini-vision.base-url}") String baseUrl,
            @Value("${external.gemini-vision.api-key:}") String apiKey,
            @Value("${external.gemini-vision.model}") String model,
            @Value("${external.gemini-vision.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.gemini-vision.read-timeout:20s}") Duration readTimeout) {
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

    public List<DetectedBoothBox> detectBooths(byte[] imageBytes, String mimeType) {
        if (apiKey.isBlank()) {
            log.warn("Gemini API key가 설정되지 않아 평면도 자동 배치를 실행할 수 없습니다.");
            throw new BusinessException(GlobalErrorCode.VENUE_MAP_AUTO_LAYOUT_UNAVAILABLE);
        }

        Request request = new Request(
            List.of(new RequestContent(List.of(
                new RequestPart(DETECTION_PROMPT, null),
                new RequestPart(null, new InlineData(mimeType, Base64.getEncoder().encodeToString(imageBytes)))
            ))),
            new GenerationConfig("application/json", RESPONSE_SCHEMA, MAX_OUTPUT_TOKENS)
        );

        Response response;
        try {
            response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(request)
                .retrieve()
                .body(Response.class);
        } catch (RestClientException exception) {
            log.warn("Gemini Vision 호출에 실패했습니다. model={}, message={}", model, exception.getMessage());
            throw new BusinessException(GlobalErrorCode.VENUE_MAP_AUTO_LAYOUT_UNAVAILABLE, exception);
        }

        String text = extractText(response);
        if (text == null || text.isBlank()) {
            log.warn("Gemini Vision 응답에 사용할 수 있는 content가 없습니다. model={}", model);
            throw new BusinessException(GlobalErrorCode.VENUE_MAP_AUTO_LAYOUT_UNAVAILABLE);
        }

        List<RawDetection> detections;
        try {
            detections = objectMapper.readValue(text, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, RawDetection.class));
        } catch (Exception exception) {
            log.warn("Gemini Vision 응답 JSON 파싱에 실패했습니다. model={}, message={}", model, exception.getMessage());
            throw new BusinessException(GlobalErrorCode.VENUE_MAP_AUTO_LAYOUT_UNAVAILABLE, exception);
        }

        return detections.stream()
            .map(this::toDetectedBoothBox)
            .filter(box -> box != null)
            .toList();
    }

    private String extractText(Response response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            return null;
        }
        return candidate.content().parts().get(0).text();
    }

    // box_2d [y0,x0,y1,x1] (0~1000 정규화)의 중심점을 부스 위치로 사용한다.
    // 라벨 없음/좌표 개수가 4개가 아닌 항목, 그리고 그 중 null이 섞인 항목은 통째로
    // 실패시키지 않고 건너뛴다 - responseSchema로도 개별 좌표값의 null 여부까지는
    // 강제되지 않아, 실측 상 밀집 도면에서 드물게 형식이 어긋난 항목이 섞여 나올 수 있었다.
    private DetectedBoothBox toDetectedBoothBox(RawDetection detection) {
        if (detection.label() == null || detection.label().isBlank()
                || detection.box2d() == null || detection.box2d().size() != 4
                || detection.box2d().contains(null)) {
            return null;
        }
        List<Integer> box = detection.box2d();
        BigDecimal yRatio = center(box.get(0), box.get(2));
        BigDecimal xRatio = center(box.get(1), box.get(3));
        if (yRatio == null || xRatio == null) {
            return null;
        }
        return new DetectedBoothBox(detection.label().trim(), xRatio, yRatio);
    }

    private BigDecimal center(int a, int b) {
        BigDecimal ratio = BigDecimal.valueOf((a + b) / 2.0 / 1000.0)
            .setScale(6, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            return null;
        }
        return ratio;
    }
}
