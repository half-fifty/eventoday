package com.min.edu.funnel.controller;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.funnel.dto.FunnelEventRankingResponse;
import com.min.edu.funnel.dto.FunnelSessionSummaryResponse;
import com.min.edu.funnel.service.FunnelSessionReconstructionService;
import com.min.edu.funnel.service.FunnelSessionSummaryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/funnel-sessions")
@RequiredArgsConstructor
public class FunnelSessionSummaryController {

    private final FunnelSessionSummaryService funnelSessionSummaryService;
    private final FunnelSessionReconstructionService funnelSessionReconstructionService;

    @GetMapping("/{eventId}/summary")
    public ApiResponse<FunnelSessionSummaryResponse> summary(
            @PathVariable Long eventId,
            @RequestParam LocalDate date,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(funnelSessionSummaryService.summarizeForAdmin(eventId, date, actor));
    }

    // 선택한 날짜 기준 전체 행사 방문수 랭킹 (관리자센터 "전체 행사 현황"용).
    @GetMapping("/summary")
    public ApiResponse<FunnelEventRankingResponse> ranking(
            @RequestParam LocalDate date,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(funnelSessionSummaryService.rankEventsByDate(date, actor));
    }

    // 새벽 배치를 기다리지 않고 특정 날짜치를 즉시 재구성해볼 수 있는 관리자용 수동 트리거 (개발/확인용).
    @PostMapping("/{eventId}/reconstruct")
    public ApiResponse<Void> reconstruct(
            @PathVariable Long eventId,
            @RequestParam LocalDate date,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        funnelSessionReconstructionService.reconstructForAdmin(eventId, date, actor);
        return ApiResponse.success(null);
    }
}
