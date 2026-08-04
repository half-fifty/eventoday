package com.min.edu.booth.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothStatus;
import com.min.edu.booth.dto.BoothBulkCreateRequestDto;
import com.min.edu.booth.dto.BoothCreateRequestDto;
import com.min.edu.booth.dto.BoothPageResponse;
import com.min.edu.booth.dto.BoothResponseDto;
import com.min.edu.booth.dto.BoothStatusUpdateRequestDto;
import com.min.edu.booth.dto.BoothUpdateRequestDto;
import com.min.edu.booth.service.BoothService;
import com.min.edu.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BoothController {

    private final BoothService boothService;

    // WBS-067
    @GetMapping("/events/{eventId}/booths")
    public ApiResponse<BoothPageResponse> list(
            @PathVariable Long eventId,
            @RequestParam(required = false) BoothStatus status,
            @RequestParam(required = false) String floorName,
            @RequestParam(required = false) String zoneName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
            boothService.list(eventId, status, floorName, zoneName, page, size, member)
        );
    }

    // WBS-068
    @GetMapping("/events/{eventId}/booths/{boothId}")
    public ApiResponse<BoothResponseDto> getDetail(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothService.getDetail(eventId, boothId, member));
    }

    // WBS-069
    @PostMapping("/events/{eventId}/booths")
    public ApiResponse<BoothResponseDto> create(
            @PathVariable Long eventId,
            @Valid @RequestBody BoothCreateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothService.create(eventId, request, member));
    }

    // WBS-070
    @PostMapping("/events/{eventId}/booths/bulk")
    public ApiResponse<List<BoothResponseDto>> createBulk(
            @PathVariable Long eventId,
            @Valid @RequestBody BoothBulkCreateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothService.createBulk(eventId, request, member));
    }

    // WBS-071
    @PatchMapping("/events/{eventId}/booths/{boothId}")
    public ApiResponse<BoothResponseDto> update(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothUpdateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothService.update(eventId, boothId, request, member));
    }

    // WBS-072
    @PatchMapping("/events/{eventId}/booths/{boothId}/status")
    public ApiResponse<BoothResponseDto> updateStatus(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothStatusUpdateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothService.updateStatus(eventId, boothId, request, member));
    }

    // WBS-073
    @DeleteMapping("/events/{eventId}/booths/{boothId}")
    public ApiResponse<Void> delete(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        boothService.delete(eventId, boothId, member);
        return ApiResponse.success();
    }
}
