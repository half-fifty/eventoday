package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.dto.EventContentDtos;
import com.min.edu.event.service.EventContentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequiredArgsConstructor
public class EventContentController {

    private final EventContentService eventContentService;

    // CONTENT-API-001: 공지·자료 목록
    @GetMapping("/events/{eventId}/contents")
    public ApiResponse<List<EventContentDtos.Summary>> listContents(
            @PathVariable Long eventId,
            @RequestParam(required = false) EventContentType contentType,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                eventContentService.listContents(eventId, contentType, member)
        );
    }

    // CONTENT-API-006: 전체 공지·자료 목록 (공개 행사 대상, 행사 이름 포함)
    // 공지사항 페이지가 행사별로 N번 호출하던 것을 1회로 대체
    // 공개 API이므로 페이지네이션 필수 (size 최대 100)
    @GetMapping("/contents")
    public ApiResponse<EventContentDtos.BoardPageResponse> listAllContents(
            @RequestParam(required = false) EventContentType contentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                eventContentService.listAllContents(contentType, page, size, member)
        );
    }

    // CONTENT-API-002: 공지·자료 상세
    @GetMapping("/event-contents/{contentId}")
    public ApiResponse<EventContentDtos.Summary> getContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                eventContentService.getContent(contentId, member)
        );
    }

    // CONTENT-API-003: 공지·자료 등록
    @PostMapping(value = "/events/{eventId}/contents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EventContentDtos.Summary> createContent(
            @PathVariable Long eventId,
            @RequestPart("data") @Valid EventContentDtos.CreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                eventContentService.createContent(eventId, request, file, member)
        );
    }

    // CONTENT-API-004: 공지·자료 수정
    @PatchMapping(value = "/event-contents/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EventContentDtos.Summary> updateContent(
            @PathVariable Long contentId,
            @RequestPart("data") @Valid EventContentDtos.UpdateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        return ApiResponse.success(
                eventContentService.updateContent(contentId, request, file, member)
        );
    }

    // CONTENT-API-005: 공지·자료 삭제
    @DeleteMapping("/event-contents/{contentId}")
    public ApiResponse<Void> deleteContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {
        eventContentService.deleteContent(contentId, member);
        return ApiResponse.success();
    }
}