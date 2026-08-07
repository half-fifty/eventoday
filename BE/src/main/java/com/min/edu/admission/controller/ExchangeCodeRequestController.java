package com.min.edu.admission.controller;

import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.admission.service.ExchangeCodeIssuanceService;
import com.min.edu.admission.service.ExchangeCodeRequestService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeCodeRequestController {

    private final ExchangeCodeRequestService exchangeCodeRequestService;
    private final ExchangeCodeIssuanceService exchangeCodeIssuanceService;

    public ExchangeCodeRequestController(
            ExchangeCodeRequestService exchangeCodeRequestService,
            ExchangeCodeIssuanceService exchangeCodeIssuanceService) {
        this.exchangeCodeRequestService = exchangeCodeRequestService;
        this.exchangeCodeIssuanceService = exchangeCodeIssuanceService;
    }

    @PostMapping("/events/{eventId}/exchange-code-requests")
    public ApiResponse<ExchangeCodeRequestDtos.CreateResponse> createRequest(
            @PathVariable Long eventId,
            @Valid @RequestBody ExchangeCodeRequestDtos.CreateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            exchangeCodeRequestService.createRequest(eventId, request, actor)
        );
    }

    @GetMapping("/events/{eventId}/exchange-code-requests")
    public ApiResponse<Page<ExchangeCodeRequestDtos.Response>> getEventRequests(
            @PathVariable Long eventId,
            @RequestParam(required = false) ExchangeCodeRequestStatus status,
            @AuthenticationPrincipal AuthenticatedMemberDto actor,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        return ApiResponse.success(
            exchangeCodeRequestService.getEventRequests(eventId, status, actor, pageable)
        );
    }

    @GetMapping("/exchange-code-requests/{requestId}")
    public ApiResponse<ExchangeCodeRequestDtos.Response> getRequestDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            exchangeCodeRequestService.getRequestDetail(requestId, actor)
        );
    }

    @GetMapping("/admin/exchange-code-requests")
    public ApiResponse<Page<ExchangeCodeRequestDtos.Response>> getAdminRequests(
            @RequestParam(required = false) ExchangeCodeRequestStatus status,
            @AuthenticationPrincipal AuthenticatedMemberDto actor,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        return ApiResponse.success(
            exchangeCodeRequestService.getAdminRequests(status, actor, pageable)
        );
    }

    @PostMapping("/admin/exchange-code-requests/{requestId}/approval")
    public ApiResponse<Void> approveRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        exchangeCodeRequestService.approveRequest(requestId, actor);
        return ApiResponse.success();
    }

    @PostMapping("/admin/exchange-code-requests/{requestId}/rejection")
    public ApiResponse<Void> rejectRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ExchangeCodeRequestDtos.RejectionRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        exchangeCodeRequestService.rejectRequest(requestId, request.reason(), actor);
        return ApiResponse.success();
    }

    @PostMapping("/admin/exchange-code-requests/{requestId}/issuance")
    public ApiResponse<ExchangeCodeRequestDtos.IssuanceResponse> issueRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(exchangeCodeIssuanceService.issue(requestId, actor));
    }
}
