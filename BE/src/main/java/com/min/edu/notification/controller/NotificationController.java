package com.min.edu.notification.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.service.NotificationService;
import com.min.edu.notification.sse.SseEmitterManager;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final SseEmitterManager sseEmitterManager;

    @GetMapping
    public ApiResponse<Page<NotificationResponseDto>> getNotifications(
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember, Pageable pageable) {
        Page<NotificationResponseDto> response = notificationService.getNotifications(authenticatedMember.getMemberId(),
                pageable);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponseDto> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {
        NotificationResponseDto response = notificationService.markAsRead(
                authenticatedMember.getMemberId(),
                notificationId);

        return ApiResponse.success(response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {
        return sseEmitterManager.connect(authenticatedMember.getMemberId());
    }
}
