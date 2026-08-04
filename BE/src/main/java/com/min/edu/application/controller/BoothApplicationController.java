package com.min.edu.application.controller;

import com.min.edu.application.dto.BoothApplicationResponseDto;
import com.min.edu.application.dto.BoothApplicationSubmitRequestDto;
import com.min.edu.application.service.BoothApplicationService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;

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
}