package com.min.edu.funnel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.funnel.dto.FunnelSessionSummaryResponse;
import com.min.edu.funnel.service.FunnelSessionSummaryService;
import com.min.edu.member.domain.PlatformRole;

class OrganizerFunnelSessionControllerTest {

    private static final Long ORGANIZATION_ID = 7L;
    private static final Long EVENT_ID = 42L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 21);

    private FunnelSessionSummaryService funnelSessionSummaryService;
    private MockMvc mockMvc;
    private AuthenticatedMemberDto principal;

    @BeforeEach
    void setUp() {
        funnelSessionSummaryService = mock(FunnelSessionSummaryService.class);
        principal = new AuthenticatedMemberDto(3L, PlatformRole.USER);

        OrganizerFunnelSessionController controller =
                new OrganizerFunnelSessionController(funnelSessionSummaryService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void summary_organizerOwnsEvent_returns200() throws Exception {
        given(funnelSessionSummaryService.summarizeForOrganizer(ORGANIZATION_ID, EVENT_ID, TARGET_DATE, principal))
                .willReturn(new FunnelSessionSummaryResponse(EVENT_ID, TARGET_DATE, 5, 4, 2, 1, 4, 1, 0));

        mockMvc.perform(get(
                        "/v1/organizations/{organizationId}/events/{eventId}/funnel-sessions/summary",
                        ORGANIZATION_ID, EVENT_ID)
                        .param("date", TARGET_DATE.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void summary_notOperationalManager_returns403() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
                .given(funnelSessionSummaryService)
                .summarizeForOrganizer(eq(ORGANIZATION_ID), eq(EVENT_ID), eq(TARGET_DATE), eq(principal));

        mockMvc.perform(get(
                        "/v1/organizations/{organizationId}/events/{eventId}/funnel-sessions/summary",
                        ORGANIZATION_ID, EVENT_ID)
                        .param("date", TARGET_DATE.toString()))
                .andExpect(status().isForbidden());
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
                return principal;
            }
        };
    }
}
