package com.min.edu.booth.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.VenueMapType;
import com.min.edu.booth.dto.BoothMapPositionUpsertRequestDto;
import com.min.edu.booth.dto.VenueMapAutoLayoutSuggestionDto;
import com.min.edu.booth.dto.VenueMapCreateRequestDto;
import com.min.edu.booth.dto.VenueMapResponseDto;
import com.min.edu.booth.service.VenueMapService;
import com.min.edu.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class VenueMapController {

    private final VenueMapService venueMapService;

    @GetMapping("/events/{eventId}/venue-maps")
    public ApiResponse<List<VenueMapResponseDto>> list(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(venueMapService.list(eventId, member));
    }

    // 공개 조회: 관람객/부스 모집 화면에서 게시된 평면도만 노출.
    // 같은 mapType이라도 층별로 각각 게시될 수 있어 목록으로 반환한다.
    @GetMapping("/events/{eventId}/venue-maps/public")
    public ApiResponse<List<VenueMapResponseDto>> getPublished(
            @PathVariable Long eventId,
            @RequestParam VenueMapType mapType) {
        return ApiResponse.success(venueMapService.getPublished(eventId, mapType));
    }

    @PostMapping("/events/{eventId}/venue-maps")
    public ApiResponse<VenueMapResponseDto> create(
            @PathVariable Long eventId,
            @Valid @RequestBody VenueMapCreateRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(venueMapService.create(eventId, request, member));
    }

    @PatchMapping("/events/{eventId}/venue-maps/{mapId}/publish")
    public ApiResponse<VenueMapResponseDto> publish(
            @PathVariable Long eventId,
            @PathVariable Long mapId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(venueMapService.publish(eventId, mapId, member));
    }

    @DeleteMapping("/events/{eventId}/venue-maps/{mapId}")
    public ApiResponse<Void> delete(
            @PathVariable Long eventId,
            @PathVariable Long mapId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        venueMapService.delete(eventId, mapId, member);
        return ApiResponse.success();
    }

    // 좌표를 저장하지 않는 "제안" 조회이지만, 요청마다 외부 Vision API를 호출하는 비용이 있어
    // GET이 아닌 POST로 둔다.
    @PostMapping("/events/{eventId}/venue-maps/{mapId}/auto-layout-suggestions")
    public ApiResponse<List<VenueMapAutoLayoutSuggestionDto>> suggestAutoLayout(
            @PathVariable Long eventId,
            @PathVariable Long mapId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(venueMapService.suggestAutoLayout(eventId, mapId, member));
    }

    @PutMapping("/events/{eventId}/venue-maps/{mapId}/positions")
    public ApiResponse<VenueMapResponseDto> upsertPositions(
            @PathVariable Long eventId,
            @PathVariable Long mapId,
            @Valid @RequestBody BoothMapPositionUpsertRequestDto request,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(venueMapService.upsertPositions(eventId, mapId, request, member));
    }
}
