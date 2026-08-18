package com.min.edu.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.ai.dto.AiFailureExplanationRequest;
import com.min.edu.ai.dto.AiFailureExplanationResponse;
import com.min.edu.ai.service.AiAdmissionFailureExplanationService;
import com.min.edu.ai.service.AiRefundFailureExplanationService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class AiFailureExplanationControllerTest {

    private AiRefundFailureExplanationService refundService;
    private AiAdmissionFailureExplanationService admissionService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        refundService = org.mockito.Mockito.mock(AiRefundFailureExplanationService.class);
        admissionService = org.mockito.Mockito.mock(AiAdmissionFailureExplanationService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AiFailureExplanationController(refundService, admissionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refundExplanationPassesMemberAndDoesNotExposeInternalFields() throws Exception {
        authenticate(10L);
        given(refundService.explain(eq(10L), eq(null), eq(1L), any()))
            .willReturn(response("EXCHANGE_CODE_ALREADY_REDEEMED", true));

        mockMvc.perform(post("/payments/1/refunds/ai-explanation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reasonCode").value("EXCHANGE_CODE_ALREADY_REDEEMED"))
            .andExpect(jsonPath("$.data.aiGenerated").value(true))
            .andExpect(jsonPath("$.data.paymentKey").doesNotExist())
            .andExpect(jsonPath("$.data.orderAccessToken").doesNotExist());

        verify(refundService).explain(eq(10L), eq(null), eq(1L), any());
    }

    @Test
    void refundExplanationPassesGuestToken() throws Exception {
        given(refundService.explain(eq(null), eq("token"), eq(1L), any()))
            .willReturn(response("OPERATION_CUTOFF_PASSED", false));

        mockMvc.perform(post("/payments/1/refunds/ai-explanation")
                .header("X-Order-Access-Token", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aiGenerated").value(false));

        verify(refundService).explain(eq(null), eq("token"), eq(1L), any());
    }

    @Test
    void refundExplanationKeepsAccessErrorsAsBackendErrors() throws Exception {
        given(refundService.explain(eq(null), eq(null), eq(1L), any()))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED));

        mockMvc.perform(post("/payments/1/refunds/ai-explanation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("TICKET_401_001"));
    }

    @Test
    void memberAdmissionExplanationPassesPrincipal() throws Exception {
        authenticate(10L);
        given(admissionService.explainForMember(eq(10L), eq(11L), any()))
            .willReturn(response("ALREADY_USED", true));

        mockMvc.perform(post("/members/me/admission-tickets/11/ai-failure-explanation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reasonCode").value("ALREADY_USED"));

        verify(admissionService).explainForMember(eq(10L), eq(11L), any());
    }

    @Test
    void guestAdmissionExplanationPassesOrderTokenAndTicket() throws Exception {
        given(admissionService.explainForGuest(eq("ORDER-1"), eq("token"), eq(11L), any()))
            .willReturn(response("TICKET_NOT_ISSUED", true));

        mockMvc.perform(post("/ticket-orders/ORDER-1/admission-tickets/11/ai-failure-explanation")
                .header("X-Order-Access-Token", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reasonCode").value("TICKET_NOT_ISSUED"));

        verify(admissionService).explainForGuest(eq("ORDER-1"), eq("token"), eq(11L), any());
    }

    @Test
    void explanationRequestRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/payments/1/refunds/ai-explanation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(
            new AiFailureExplanationRequest("왜 실패했나요?")
        );
    }

    private AiFailureExplanationResponse response(String reasonCode, boolean aiGenerated) {
        return new AiFailureExplanationResponse(
            "explanation",
            "action",
            false,
            aiGenerated,
            reasonCode
        );
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
