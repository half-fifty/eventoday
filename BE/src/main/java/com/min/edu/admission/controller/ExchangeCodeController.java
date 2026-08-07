package com.min.edu.admission.controller;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.service.ExchangeCodeQueryService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeCodeController {

    private final ExchangeCodeQueryService exchangeCodeQueryService;

    public ExchangeCodeController(ExchangeCodeQueryService exchangeCodeQueryService) {
        this.exchangeCodeQueryService = exchangeCodeQueryService;
    }

    @GetMapping("/events/{eventId}/exchange-codes")
    public ApiResponse<Page<ExchangeCodeDtos.EventListResponse>> getEventExchangeCodes(
            @PathVariable Long eventId,
            @RequestParam(required = false) ExchangeCodeStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            exchangeCodeQueryService.getEventExchangeCodes(eventId, status, actor, page, size)
        );
    }

    @GetMapping("/members/me/exchange-codes")
    public ApiResponse<Page<ExchangeCodeDtos.MyListResponse>> getMyExchangeCodes(
            @RequestParam(required = false) ExchangeCodeStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            exchangeCodeQueryService.getMyExchangeCodes(status, actor, page, size)
        );
    }
}
