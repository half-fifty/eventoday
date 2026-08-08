package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.MobileGuideBoothDetailResponse;
import com.min.edu.booth.dto.MobileGuideBoothListResponse;
import com.min.edu.booth.dto.MobileGuideMainResponse;
import com.min.edu.booth.service.MobileGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}/guide")
public class MobileGuideController {

    private final MobileGuideService mobileGuideService;

    // GUIDE-API-001: 모바일 안내 메인 데이터
    @GetMapping
    public ResponseEntity<MobileGuideMainResponse> getGuideMain(
            @PathVariable Long eventId) {

        MobileGuideMainResponse response = mobileGuideService.getGuideMain(eventId);
        return ResponseEntity.ok(response);
    }

    // GUIDE-API-002: 모바일 부스 검색·목록
    @GetMapping("/booths")
    public ResponseEntity<List<MobileGuideBoothListResponse>> searchBooths(
            @PathVariable Long eventId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        Long memberId = principal != null ? principal.getMemberId() : null;
        List<MobileGuideBoothListResponse> response =
                mobileGuideService.searchBooths(eventId, keyword, memberId);
        return ResponseEntity.ok(response);
    }

    // GUIDE-API-003: 모바일 부스 상세
    @GetMapping("/booths/{boothId}")
    public ResponseEntity<MobileGuideBoothDetailResponse> getBoothDetail(
            @PathVariable Long eventId,
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        Long memberId = principal != null ? principal.getMemberId() : null;
        MobileGuideBoothDetailResponse response =
                mobileGuideService.getBoothDetail(eventId, boothId, memberId);
        return ResponseEntity.ok(response);
    }
}
