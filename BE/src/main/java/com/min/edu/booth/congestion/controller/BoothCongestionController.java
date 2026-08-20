package com.min.edu.booth.congestion.controller;

import com.min.edu.booth.congestion.dto.response.*;
import com.min.edu.booth.congestion.service.BoothCongestionService;
import com.min.edu.booth.congestion.service.BoothCongestionForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/booths")
@RequiredArgsConstructor
public class BoothCongestionController {

    private final BoothCongestionService boothCongestionService;
    private final BoothCongestionForecastService boothCongestionForecastService;

    /**
     * 특정 부스의 현재 혼잡도 조회
     * GET /booths/{boothId}/congestion
     */
    @GetMapping("/{boothId}/congestion")
    public ResponseEntity<BoothCongestionResponse> getCongestion(@PathVariable Long boothId) {
        var congestion = boothCongestionService.getLatestCongestion(boothId);
        return ResponseEntity.ok(BoothCongestionResponse.from(congestion));
    }

    /**
     * 이벤트의 인기 부스 조회 (혼잡도 높은 순서, TOP 10)
     * GET /booths/events/{eventId}/popular-booths
     */
    @GetMapping("/events/{eventId}/popular-booths")
    public ResponseEntity<List<PopularBoothResponse>> getPopularBooths(@PathVariable Long eventId) {
        var popularBooths = boothCongestionService.getPopularBooths(eventId);

        var responses = new java.util.ArrayList<PopularBoothResponse>(popularBooths.size());
        for (int i = 0; i < popularBooths.size(); i++) {
            responses.add(PopularBoothResponse.from(popularBooths.get(i), i + 1));
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * 이벤트의 한산한 부스 조회 (혼잡도 낮은 순서, TOP 10)
     * GET /booths/events/{eventId}/uncrowded-booths
     */
    @GetMapping("/events/{eventId}/uncrowded-booths")
    public ResponseEntity<List<UncrowdedBoothResponse>> getUncrowdedBooths(@PathVariable Long eventId) {
        var uncrowdedBooths = boothCongestionService.getUncrowdedBooths(eventId);

        var responses = uncrowdedBooths.stream()
                .map(UncrowdedBoothResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * 부스의 시간대별 혼잡도 예측 조회
     * GET /booths/{boothId}/forecast?date=2026-08-13
     */
    @GetMapping("/{boothId}/forecast")
    public ResponseEntity<ForecastListResponse> getForecast(
            @PathVariable Long boothId,
            // @RequestParam의 defaultValue는 SpEL을 평가하지 않고 리터럴 문자열을 그대로 LocalDate로
            // 파싱하려 들기 때문에, 예전 "#{T(java.time.LocalDate).now()}" 값은 파라미터를 생략하면
            // 파싱 오류로 이어졌다. required=false로 받고 메서드 안에서 직접 기본값을 채운다.
            @RequestParam(required = false) LocalDate date
    ) {
        LocalDate resolvedDate = date != null ? date : LocalDate.now();
        var forecasts = boothCongestionForecastService.getForecastByDate(boothId, resolvedDate);
        var bestTimeSlot = boothCongestionForecastService.getBestTimeSlot(boothId, resolvedDate);

        var responses = forecasts.stream()
                .map(CongestionForecastResponse::from)
                .toList();

        return ResponseEntity.ok(
                ForecastListResponse.builder()
                        .forecastDate(resolvedDate)
                        .forecasts(responses)
                        .bestTimeSlot(bestTimeSlot.map(CongestionForecastResponse::from).orElse(null))
                        .build()
        );
    }
}