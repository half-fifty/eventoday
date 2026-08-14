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
     * GET /events/{eventId}/popular-booths
     */
    @GetMapping("/events/{eventId}/popular-booths")
    public ResponseEntity<List<PopularBoothResponse>> getPopularBooths(@PathVariable Long eventId) {
        var popularBooths = boothCongestionService.getPopularBooths(eventId);

        var responses = popularBooths.stream()
                .map((booth) -> {
                    int rank = popularBooths.indexOf(booth) + 1;
                    return PopularBoothResponse.from(booth, rank);
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * 이벤트의 한산한 부스 조회 (혼잡도 낮은 순서, TOP 10)
     * GET /events/{eventId}/uncrowded-booths
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
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}") LocalDate date
    ) {
        var forecasts = boothCongestionForecastService.getForecastByDate(boothId, date);
        var bestTimeSlot = boothCongestionForecastService.getBestTimeSlot(boothId, date);

        var responses = forecasts.stream()
                .map(CongestionForecastResponse::from)
                .toList();

        return ResponseEntity.ok(
                ForecastListResponse.builder()
                        .forecastDate(date)
                        .forecasts(responses)
                        .bestTimeSlot(bestTimeSlot.map(CongestionForecastResponse::from).orElse(null))
                        .build()
        );
    }
}