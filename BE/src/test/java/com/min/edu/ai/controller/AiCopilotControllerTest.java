package com.min.edu.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.min.edu.ai.dto.AiCategory;
import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.service.AiCopilotService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class AiCopilotControllerTest {

    private AiCopilotService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AiCopilotService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AiCopilotController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsRequestToServiceAndReturnsStructuredResponse() throws Exception {
        authenticate(10L);
        given(service.ask(eq(100L), any(AiCopilotRequest.class), any(AuthenticatedMemberDto.class)))
            .willReturn(new AiCopilotResponse("이미 사용된 티켓입니다.", AiCategory.ADMISSION, false));

        mockMvc.perform(post("/events/100/ai/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "이 티켓은 이미 사용된 건가요?",
                      "context": {"admissionTicketId": 11}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value("이미 사용된 티켓입니다."))
            .andExpect(jsonPath("$.data.category").value("ADMISSION"))
            .andExpect(jsonPath("$.data.needsHumanSupport").value(false));

        verify(service).ask(eq(100L), any(AiCopilotRequest.class), any(AuthenticatedMemberDto.class));
    }

    @Test
    void returnsUnauthorizedWhenServiceRejectsAnonymousActor() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED))
            .given(service).ask(eq(100L), any(AiCopilotRequest.class), eq(null));

        mockMvc.perform(post("/events/100/ai/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"이 QR은 왜 입장이 안 되나요?\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void returnsForbiddenForGeneralUserWithoutOperationAccess() throws Exception {
        authenticate(10L);
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
            .given(service).ask(eq(100L), any(AiCopilotRequest.class), any(AuthenticatedMemberDto.class));

        mockMvc.perform(post("/events/100/ai/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"이 티켓으로 지금 입장 가능한가요?\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        authenticate(10L);

        mockMvc.perform(post("/events/100/ai/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    private void authenticate(Long memberId) {
        AuthenticatedMemberDto principal =
            new AuthenticatedMemberDto(memberId, PlatformRole.USER);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    return null;
                }
                return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            }
        };
    }
}
