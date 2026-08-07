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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

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
}