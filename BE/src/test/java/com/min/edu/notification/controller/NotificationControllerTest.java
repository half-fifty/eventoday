package com.min.edu.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationResponseDto;
import com.min.edu.notification.dto.NotificationUnreadCountResponseDto;
import com.min.edu.notification.service.NotificationService;
import com.min.edu.notification.sse.SseEmitterManager;

class NotificationControllerTest {

    private static final Long MEMBER_ID = 10L;

    private NotificationService notificationService;
    private SseEmitterManager sseEmitterManager;
    private NotificationController notificationController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notificationService = org.mockito.Mockito.mock(NotificationService.class);
        sseEmitterManager = org.mockito.Mockito.mock(SseEmitterManager.class);
        notificationController = new NotificationController(
                notificationService,
                sseEmitterManager);
        mockMvc = MockMvcBuilders
                .standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        authenticationPrincipalResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getNotifications_returnsAuthenticatedMemberNotifications() throws Exception {
        PageRequest pageable = PageRequest.of(0, 5);
        given(notificationService.getNotifications(MEMBER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(unreadResponse()), pageable, 1));

        mockMvc.perform(get("/notifications")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.content[0].id").value(7))
                .andExpect(jsonPath("$.data.content[0].title").value("일정 변경"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.content[0].readAt").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(notificationService).getNotifications(MEMBER_ID, pageable);
    }

    @Test
    void getUnreadCount_returnsAuthenticatedMemberUnreadCount() throws Exception {
        given(notificationService.getUnreadCount(MEMBER_ID))
                .willReturn(new NotificationUnreadCountResponseDto(3L));

        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.count").value(3));

        verify(notificationService).getUnreadCount(MEMBER_ID);
    }

    @Test
    void markAsRead_returnsReadNotification() throws Exception {
        NotificationResponseDto readResponse = new NotificationResponseDto(
                7L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.",
                true,
                OffsetDateTime.parse("2026-08-05T10:01:00+09:00"),
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"));
        given(notificationService.markAsRead(MEMBER_ID, 7L)).willReturn(readResponse);

        mockMvc.perform(patch("/notifications/7/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        verify(notificationService).markAsRead(MEMBER_ID, 7L);
    }

    @Test
    void markAsRead_returnsNotFoundWhenNotificationIsNotOwned() throws Exception {
        given(notificationService.markAsRead(MEMBER_ID, 99L))
                .willThrow(new BusinessException(GlobalErrorCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(patch("/notifications/99/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(GlobalErrorCode.NOTIFICATION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void stream_returnsEmitterCreatedForAuthenticatedMember() throws Exception {
        SseEmitter emitter = new SseEmitter(60_000L);
        given(sseEmitterManager.connect(MEMBER_ID)).willReturn(emitter);

        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(sseEmitterManager).connect(MEMBER_ID);
    }

    private NotificationResponseDto unreadResponse() {
        return new NotificationResponseDto(
                7L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.",
                false,
                null,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"));
    }

    private AuthenticatedMemberDto authenticatedMember() {
        return new AuthenticatedMemberDto(MEMBER_ID, PlatformRole.USER);
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return authenticatedMember();
            }
        };
    }
}
