package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.dto.EventContentDtos;
import com.min.edu.event.service.EventContentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}