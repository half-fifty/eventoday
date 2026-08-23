package com.min.edu.funnel.controller;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.funnel.dto.FunnelSessionSummaryResponse;
import com.min.edu.funnel.service.FunnelSessionSummaryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/organizations/{organizationId}/events/{eventId}/funnel-sessions")
@RequiredArgsConstructor
public class OrganizerFunnelSessionController {

    private final FunnelSessionSummaryService funnelSessionSummaryService;

    @GetMapping("/summary")
    public ApiResponse<FunnelSessionSummaryResponse> summary(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @RequestParam LocalDate date,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
                funnelSessionSummaryService.summarizeForOrganizer(organizationId, eventId, date, actor));
    }
}
