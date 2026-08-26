package com.min.edu.organization.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.organization.domain.OrganizationSignupReviewStatus;
import com.min.edu.organization.dto.OrganizationSignupRejectionRequestDto;
import com.min.edu.organization.dto.OrganizationSignupReviewDetailDto;
import com.min.edu.organization.dto.OrganizationSignupReviewSummaryDto;
import com.min.edu.organization.service.OrganizationSignupReviewAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/organization-signups")
@RequiredArgsConstructor
public class OrganizationSignupReviewAdminController {

    private final OrganizationSignupReviewAdminService reviewAdminService;

    @GetMapping
    public ApiResponse<Page<OrganizationSignupReviewSummaryDto>> list(
            @RequestParam(required = false) OrganizationSignupReviewStatus status,
            @AuthenticationPrincipal AuthenticatedMemberDto actor,
            Pageable pageable) {
        return ApiResponse.success(reviewAdminService.list(status, actor, pageable));
    }

    @GetMapping("/{applicationId}")
    public ApiResponse<OrganizationSignupReviewDetailDto> getDetail(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(reviewAdminService.getDetail(applicationId, actor));
    }

    @PostMapping("/{applicationId}/approval")
    public ApiResponse<Void> approve(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        reviewAdminService.approve(applicationId, actor);
        return ApiResponse.success();
    }

    @PostMapping("/{applicationId}/rejection")
    public ApiResponse<Void> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody OrganizationSignupRejectionRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        reviewAdminService.reject(applicationId, request.getReason(), actor);
        return ApiResponse.success();
    }
}
