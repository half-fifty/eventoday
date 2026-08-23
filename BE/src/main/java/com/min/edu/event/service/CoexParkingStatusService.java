package com.min.edu.event.service;

import com.min.edu.event.dto.VenueParkingStatusDto;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class CoexParkingStatusService {
    private static final String SOURCE_URL =
            "https://www.coex.co.kr/guide/parking-information/infomation/";
    private static final Pattern HOURLY_DATA_PATTERN = Pattern.compile(
            "label\\s*:\\s*['\"]주차 대수['\"][\\s\\S]*?data\\s*:\\s*\\[([^]]+)]");
    private static final Pattern HOUR_PATTERN = Pattern.compile("(\\d{1,2})시");
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final VenueParkingStatusDto.Thresholds DEFAULT_THRESHOLDS =
            new VenueParkingStatusDto.Thresholds(2200, 2400, 2600);

    private final RestClient restClient;
    private volatile VenueParkingStatusDto cached;

    public CoexParkingStatusService(
            @Value("${external.coex.parking-connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.coex.parking-read-timeout:8s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public VenueParkingStatusDto getStatus() {
        VenueParkingStatusDto current = cached;
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(OffsetDateTime.now())) {
            return current;
        }
        return refreshSafely();
    }

    @Scheduled(fixedDelayString = "${external.coex.parking-refresh-ms:3600000}")
    public void scheduledRefresh() {
        refreshSafely();
    }

    private synchronized VenueParkingStatusDto refreshSafely() {
        VenueParkingStatusDto current = cached;
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(OffsetDateTime.now())) {
            return current;
        }
        try {
            String html = restClient.get()
                    .uri(SOURCE_URL)
                    .header(HttpHeaders.USER_AGENT, "EvenToday-VenueGuide/1.0")
                    .retrieve()
                    .body(String.class);
            VenueParkingStatusDto refreshed = parse(html);
            cached = refreshed;
            return refreshed;
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("코엑스 주차 현황 갱신 실패: {}", exception.getMessage());
            if (current != null) {
                return current.asStale("공식 현황을 갱신하지 못해 마지막 확인 정보를 표시합니다.");
            }
            return unavailable();
        }
    }

    VenueParkingStatusDto parse(String html) {
        if (html == null || html.isBlank()) throw new IllegalStateException("빈 응답입니다.");
        Document document = Jsoup.parse(html, SOURCE_URL);
        String statusLabel = text(document.selectFirst(".ParkingApiMapContInfo-title"), "확인 불가");
        String fullHourText = text(document.selectFirst(".ParkingApiMapGraphInfo strong"), null);
        Integer expectedFullHour = parseHour(fullHourText);
        List<Integer> counts = parseHourlyCounts(document);
        if (counts.size() > 24) counts = List.copyOf(counts.subList(0, 24));
        if (counts.isEmpty()) throw new IllegalStateException("시간별 주차 데이터를 찾지 못했습니다.");
        return new VenueParkingStatusDto(
                "COEX", normalizeStatus(statusLabel), statusLabel, expectedFullHour, counts,
                DEFAULT_THRESHOLDS, OffsetDateTime.now(), false,
                "코엑스 공식 페이지의 카카오T 기반 정보를 최대 1시간 간격으로 확인합니다.", SOURCE_URL);
    }

    private List<Integer> parseHourlyCounts(Document document) {
        for (Element script : document.select("script")) {
            Matcher matcher = HOURLY_DATA_PATTERN.matcher(script.data());
            if (!matcher.find()) continue;
            List<Integer> counts = new ArrayList<>();
            for (String raw : matcher.group(1).split(",")) {
                try {
                    counts.add(Integer.parseInt(raw.trim()));
                } catch (NumberFormatException ignored) {
                    return List.of();
                }
            }
            return counts;
        }
        return List.of();
    }

    private Integer parseHour(String value) {
        if (value == null) return null;
        Matcher matcher = HOUR_PATTERN.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String normalizeStatus(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("만차")) return "FULL";
        if (normalized.contains("혼잡")) return "CONGESTED";
        if (normalized.contains("원활") || normalized.contains("여유")) return "SMOOTH";
        return "UNKNOWN";
    }

    private String text(Element element, String fallback) {
        return element == null || element.text().isBlank() ? fallback : element.text().trim();
    }

    private VenueParkingStatusDto unavailable() {
        return new VenueParkingStatusDto(
                "COEX", "UNKNOWN", "확인 불가", null, List.of(), DEFAULT_THRESHOLDS,
                OffsetDateTime.now(), true, "현재 공식 주차 현황을 불러올 수 없습니다.", SOURCE_URL);
    }
}
