package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.dto.EventDetailImageDtos;
import com.min.edu.event.service.EventDetailImageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class EventDetailImageController {
    private final EventDetailImageService detailImageService;

    public EventDetailImageController(EventDetailImageService detailImageService) {
        this.detailImageService = detailImageService;
    }

    @GetMapping("/events/{eventId}/detail-images")
    public ApiResponse<List<EventDetailImageDtos.Item>> findPublic(@PathVariable Long eventId) {
        return ApiResponse.success(detailImageService.findPublic(eventId));
    }

    @GetMapping("/organizations/{organizationId}/events/{eventId}/detail-images")
    public ApiResponse<List<EventDetailImageDtos.Item>> findManaged(
            @PathVariable Long organizationId, @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(detailImageService.findManaged(organizationId, eventId, actor));
    }

    @PutMapping("/organizations/{organizationId}/events/{eventId}/detail-images")
    public ApiResponse<List<EventDetailImageDtos.Item>> replace(
            @PathVariable Long organizationId, @PathVariable Long eventId,
            @Valid @RequestBody EventDetailImageDtos.SaveRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(detailImageService.replace(organizationId, eventId, request, actor));
    }
}
