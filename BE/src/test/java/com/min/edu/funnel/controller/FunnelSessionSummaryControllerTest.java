package com.min.edu.funnel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
import com.min.edu.funnel.dto.FunnelEventRankingItem;
import com.min.edu.funnel.dto.FunnelEventRankingResponse;
import com.min.edu.funnel.service.FunnelSessionReconstructionService;
import com.min.edu.funnel.service.FunnelSessionSummaryService;
import com.min.edu.member.domain.PlatformRole;

class FunnelSessionSummaryControllerTest {

    private static final Long EVENT_ID = 42L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 21);

    private FunnelSessionSummaryService funnelSessionSummaryService;
    private FunnelSessionReconstructionService funnelSessionReconstructionService;
    private MockMvc mockMvc;
    private AuthenticatedMemberDto principal;

    @BeforeEach
    void setUp() {
        funnelSessionSummaryService = mock(FunnelSessionSummaryService.class);
        funnelSessionReconstructionService = mock(FunnelSessionReconstructionService.class);
        principal = null;

        FunnelSessionSummaryController controller =
                new FunnelSessionSummaryController(funnelSessionSummaryService, funnelSessionReconstructionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void reconstruct_adminActor_delegatesToServiceAndReturns200() throws Exception {
        principal = new AuthenticatedMemberDto(1L, PlatformRole.PLATFORM_ADMIN);

        mockMvc.perform(post("/v1/admin/funnel-sessions/{eventId}/reconstruct", EVENT_ID)
                        .param("date", TARGET_DATE.toString()))
                .andExpect(status().isOk());

        verify(funnelSessionReconstructionService)
                .reconstructForAdmin(eq(EVENT_ID), eq(TARGET_DATE), eq(principal));
    }

    @Test
    void reconstruct_nonAdminActor_returns403() throws Exception {
        principal = new AuthenticatedMemberDto(2L, PlatformRole.USER);
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
                .given(funnelSessionReconstructionService)
                .reconstructForAdmin(any(), any(), any());

        mockMvc.perform(post("/v1/admin/funnel-sessions/{eventId}/reconstruct", EVENT_ID)
                        .param("date", TARGET_DATE.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ranking_adminActor_returnsRanking() throws Exception {
        principal = new AuthenticatedMemberDto(1L, PlatformRole.PLATFORM_ADMIN);
        FunnelEventRankingResponse response = new FunnelEventRankingResponse(
                TARGET_DATE, List.of(new FunnelEventRankingItem(EVENT_ID, "가을 박람회", 10, 2)));
        given(funnelSessionSummaryService.rankEventsByDate(TARGET_DATE, principal)).willReturn(response);

        mockMvc.perform(get("/v1/admin/funnel-sessions/summary").param("date", TARGET_DATE.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void ranking_nonAdminActor_returns403() throws Exception {
        principal = new AuthenticatedMemberDto(2L, PlatformRole.USER);
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
                .given(funnelSessionSummaryService).rankEventsByDate(any(), any());

        mockMvc.perform(get("/v1/admin/funnel-sessions/summary").param("date", TARGET_DATE.toString()))
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
