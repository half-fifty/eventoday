package com.min.edu.advertisement.controller;

import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.dto.AdvertisementDtos;
import com.min.edu.advertisement.service.AdvertisementService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class AdvertisementController {
    private final AdvertisementService advertisementService;
    public AdvertisementController(AdvertisementService advertisementService) {
        this.advertisementService = advertisementService;
    }

    @GetMapping("/advertisements/active")
    public ApiResponse<List<AdvertisementDtos.Response>> findActive(
            @RequestParam(required = false) Long eventId) {
        return ApiResponse.success(advertisementService.findActive(eventId));
    }
    @GetMapping("/advertisements/pricing")
    public ApiResponse<AdvertisementDtos.PricingResponse> getPricing() {
        return ApiResponse.success(advertisementService.getPricing());
    }
    @PostMapping("/events/{eventId}/advertisements")
    public ApiResponse<AdvertisementDtos.Response> createEventAd(@PathVariable Long eventId,
            @Valid @RequestBody AdvertisementDtos.SaveRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.createEventAd(eventId, request, actor));
    }
    @PostMapping("/booths/{boothId}/advertisements")
    public ApiResponse<AdvertisementDtos.Response> createBoothAd(@PathVariable Long boothId,
            @Valid @RequestBody AdvertisementDtos.SaveRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.createBoothAd(boothId, request, actor));
    }
    @GetMapping("/organizations/{organizationId}/advertisements")
    public ApiResponse<Page<AdvertisementDtos.Response>> findOrganizationAds(@PathVariable Long organizationId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor, Pageable pageable) {
        return ApiResponse.success(advertisementService.findOrganizationAds(organizationId, actor, pageable));
    }
    @GetMapping("/advertisements/{advertisementId}")
    public ApiResponse<AdvertisementDtos.Response> get(@PathVariable Long advertisementId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.get(advertisementId, actor));
    }
    @PatchMapping("/advertisements/{advertisementId}")
    public ApiResponse<AdvertisementDtos.Response> update(@PathVariable Long advertisementId,
            @Valid @RequestBody AdvertisementDtos.UpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.update(advertisementId, request, actor));
    }
    @PatchMapping("/advertisements/{advertisementId}/creative")
    public ApiResponse<AdvertisementDtos.Response> updateCreative(
            @PathVariable Long advertisementId,
            @Valid @RequestBody AdvertisementDtos.CreativeUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.updateCreative(advertisementId, request, actor));
    }
    @PostMapping("/advertisements/{advertisementId}/cancellation")
    public ApiResponse<Void> cancel(@PathVariable Long advertisementId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        advertisementService.cancel(advertisementId, actor); return ApiResponse.success();
    }
    @GetMapping("/admin/advertisements")
    public ApiResponse<Page<AdvertisementDtos.Response>> findAdminAds(
            @RequestParam(required = false) AdvertisementStatus status,
            @AuthenticationPrincipal AuthenticatedMemberDto actor, Pageable pageable) {
        return ApiResponse.success(advertisementService.findAdminAds(status, actor, pageable));
    }
    @PostMapping("/admin/advertisements/{advertisementId}/approval")
    public ApiResponse<Void> approve(@PathVariable Long advertisementId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        advertisementService.approve(advertisementId, actor); return ApiResponse.success();
    }
    @PostMapping("/admin/advertisements/{advertisementId}/rejection")
    public ApiResponse<Void> reject(@PathVariable Long advertisementId,
            @Valid @RequestBody AdvertisementDtos.RejectionRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        advertisementService.reject(advertisementId, request.reason(), actor); return ApiResponse.success();
    }
    @GetMapping("/events/{eventId}/booth-ad-candidates")
    public ApiResponse<List<AdvertisementDtos.BoothCandidate>> findBoothCandidates(@PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(advertisementService.findBoothCandidates(eventId, actor));
    }
}
