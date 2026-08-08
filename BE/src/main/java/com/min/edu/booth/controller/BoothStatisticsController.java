package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothStatisticsDtos;
import com.min.edu.booth.service.BoothStatisticsService;
import com.min.edu.common.response.ApiResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BoothStatisticsController {

    private final BoothStatisticsService boothStatisticsService;

    // STAT-API-001: 시간대별 부스 통계 조회
    @GetMapping("/booths/{boothId}/statistics/hourly")
    public ApiResponse<BoothStatisticsDtos.HourlySummary> getHourlyStatistics(
            @PathVariable Long boothId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {

        // date 미입력 시 오늘 날짜 사용
        LocalDate statDate = (date != null) ? date : LocalDate.now();

        return ApiResponse.success(
                boothStatisticsService.getHourlyStatistics(boothId, statDate, member));
    }
}