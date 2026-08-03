package com.min.edu.recruitment.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.recruitment.dto.BoothRecruitmentCreateRequestDto;
import com.min.edu.recruitment.dto.BoothRecruitmentResponseDto;
import com.min.edu.recruitment.dto.BoothRecruitmentUpdateRequestDto;
import com.min.edu.recruitment.service.BoothRecruitmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BoothRecruitmentController {

    private final BoothRecruitmentService boothRecruitmentService;

    // REC-API-001
    @GetMapping("/booth-recruitments")
    public ApiResponse<List<BoothRecruitmentResponseDto>> list(
            @RequestParam(required = false) BoothRecruitmentStatus status) {
        return ApiResponse.success(boothRecruitmentService.listPublic(status));
    }

    // REC-API-002
    @GetMapping("/booth-recruitments/{recruitmentId}")
    public ApiResponse<BoothRecruitmentResponseDto> getDetail(@PathVariable Long recruitmentId) {
        return ApiResponse.success(boothRecruitmentService.getPublicDetail(recruitmentId));
    }

    // REC-API-003
    @PostMapping("/events/{eventId}/booth-recruitment")
    public ApiResponse<BoothRecruitmentResponseDto> create(
            @PathVariable Long eventId,
            @Valid @RequestBody BoothRecruitmentCreateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
            boothRecruitmentService.create(eventId, request, member)
        );
    }

    // REC-API-004
    @GetMapping("/events/{eventId}/booth-recruitment/management")
    public ApiResponse<BoothRecruitmentResponseDto> getManagement(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothRecruitmentService.getManagement(eventId, member));
    }

    // REC-API-005
    @PatchMapping("/events/{eventId}/booth-recruitment")
    public ApiResponse<BoothRecruitmentResponseDto> update(
            @PathVariable Long eventId,
            @Valid @RequestBody BoothRecruitmentUpdateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
            boothRecruitmentService.update(eventId, request, member)
        );
    }

    // REC-API-006
    @PostMapping("/events/{eventId}/booth-recruitment/completion")
    public ApiResponse<BoothRecruitmentResponseDto> complete(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothRecruitmentService.complete(eventId, member));
    }

    // REC-API-007
    @PostMapping("/events/{eventId}/booth-recruitment/closure")
    public ApiResponse<BoothRecruitmentResponseDto> close(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(boothRecruitmentService.close(eventId, member));
    }
}
