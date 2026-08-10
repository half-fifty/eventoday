package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.MobileGuideBoothDetailResponse;
import com.min.edu.booth.dto.MobileGuideBoothListResponse;
import com.min.edu.booth.dto.MobileGuideMainResponse;
import com.min.edu.booth.service.MobileGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}/guide")
public class MobileGuideController {

    private final MobileGuideService mobileGuideService;

    /**
     * 1. 모바일 안내 메인 (WBS-135)
     */
    @GetMapping
    public ResponseEntity<MobileGuideMainResponse> getGuideMain(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // 1) memberId 추출 (로그인하지 않으면 null)
        Long memberId = principal != null ? principal.getMemberId() : null;

        // 2) Service 호출 (memberId 전달)
        MobileGuideMainResponse response = mobileGuideService.getGuideMain(eventId, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 모바일 부스 검색 (WBS-136)
     */
    @GetMapping("/booths")
    public ResponseEntity<Page<MobileGuideBoothListResponse>> searchBooths(
            @PathVariable Long eventId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            Pageable pageable) {

        // 1) memberId 추출 (로그인하지 않으면 null)
        Long memberId = principal != null ? principal.getMemberId() : null;

        // 2) Service 호출 (memberId + Pageable 전달)
        Page<MobileGuideBoothListResponse> response = mobileGuideService.searchBooths(
                eventId, keyword, memberId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 모바일 부스 상세 (WBS-137)
     */
    @GetMapping("/booths/{boothId}")
    public ResponseEntity<MobileGuideBoothDetailResponse> getBoothDetail(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // 1) memberId 추출 (로그인하지 않으면 null)
        Long memberId = principal != null ? principal.getMemberId() : null;

        // 2) Service 호출 (memberId 전달)
        MobileGuideBoothDetailResponse response = mobileGuideService.getBoothDetail(
                eventId, boothId, memberId);
        return ResponseEntity.ok(response);
    }
}