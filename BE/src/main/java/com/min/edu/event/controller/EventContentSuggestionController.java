package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.dto.EventContentSuggestionDto;
import com.min.edu.event.service.EventContentSuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventContentSuggestionController {
    private final EventContentSuggestionService service;
    @PostMapping("/content-suggestions")
    public ApiResponse<EventContentSuggestionDto.Response> suggest(
            @Valid @RequestBody EventContentSuggestionDto.Request request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.suggest(request, actor));
    }
}
