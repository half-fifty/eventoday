package com.min.edu.application.controller;

import com.min.edu.application.dto.BoothApplicationResponseDto;
import com.min.edu.application.dto.BoothApplicationSubmitRequestDto;
import com.min.edu.application.dto.BoothApplicationPageResponse;
import com.min.edu.application.service.BoothApplicationService;
import com.min.edu.booth.domain.BoothApplicationStatus;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
public class BoothApplicationController {

    private final BoothApplicationService boothApplicationService;

    // APP-API-001
    @PostMapping("/booth-recruitments/{recruitmentId}/applications")
    public ApiResponse<BoothApplicationResponseDto> submit(
            @PathVariable Long recruitmentId,
            @Valid @RequestBody BoothApplicationSubmitRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                boothApplicationService.submit(recruitmentId, request, member)
        );
    }

    // APP-API-002
    @GetMapping("/organizations/{organizationId}/booth-applications")
    public ApiResponse<List<BoothApplicationResponseDto>> listByOrganization(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                boothApplicationService.listByOrganization(organizationId, member)
        );
    }

    // APP-API-003
    @GetMapping("/booth-applications/{applicationId}")
    public ApiResponse<BoothApplicationResponseDto> getDetail(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                boothApplicationService.getDetail(applicationId, member)
        );
    }

    // APP-API-004
    @PostMapping("/booth-applications/{applicationId}/cancellation")
    public ApiResponse<Void> cancel(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        boothApplicationService.cancel(applicationId, member);
        return ApiResponse.success();
    }

    // APP-API-005: 행사 신청 목록·검색 (EVENT_MANAGER)
    @GetMapping("/events/{eventId}/booth-applications")
    public ApiResponse<BoothApplicationPageResponse> listByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) BoothApplicationStatus status,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) String boothCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                boothApplicationService.listByEvent(
                        eventId, status, teamName, boothCode,
                        submittedFrom, submittedTo, page, size, member)
        );
    }
}