package com.min.edu.admission.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.dto.AdmissionCheckInDtos;
import com.min.edu.admission.service.AdmissionCheckInService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

class AdmissionCheckInControllerTest {
    private AdmissionCheckInService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(AdmissionCheckInService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AdmissionCheckInController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkIn_mapsRequestAndReturnsResponse() throws Exception {
        authenticate(10L);
        given(service.checkIn(eq(1L), any(), any(AuthenticatedMemberDto.class)))
            .willReturn(checkInResponse());

        mockMvc.perform(post("/events/1/admission-checkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"opaque-token\",\"gateName\":\"A Gate\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.status").value("USED"))
            .andExpect(jsonPath("$.data.action").value("CHECK_IN"))
            .andExpect(jsonPath("$.data.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.qrToken").doesNotExist());

        verify(service).checkIn(eq(1L), any(), any(AuthenticatedMemberDto.class));
    }

    @Test
    void cancelCheckIn_mapsRequestAndReturnsResponse() throws Exception {
        authenticate(10L);
        given(service.cancelCheckIn(eq(1L), eq(11L), any(AuthenticatedMemberDto.class)))
            .willReturn(cancelResponse());

        mockMvc.perform(post("/events/1/admission-tickets/11/check-in-cancellation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.action").value("CHECK_IN_CANCEL"))
            .andExpect(jsonPath("$.data.result").value("SUCCESS"));
    }

    @Test
    void getEventAdmissionLogs_mapsFiltersAndPaging() throws Exception {
        authenticate(10L);
        given(service.getEventAdmissionLogs(
            eq(1L),
            eq(AdmissionAction.CHECK_IN),
            eq(AdmissionResult.SUCCESS),
            any(AuthenticatedMemberDto.class),
            eq(1),
            eq(10)))
            .willReturn(new PageImpl<>(List.of(logResponse()), PageRequest.of(1, 10), 1));

        mockMvc.perform(get("/events/1/admission-logs")
                .param("action", "CHECK_IN")
                .param("result", "SUCCESS")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].admissionLogId").value(21))
            .andExpect(jsonPath("$.data.content[0].admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.content[0].staffNickname").value("staff"))
            .andExpect(jsonPath("$.data.content[0].staffMemberId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].qrToken").doesNotExist());
    }

    @Test
    void admissionCheckInApis_doNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(post("/v1/events/1/admission-checkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"opaque-token\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/events/1/admission-tickets/11/check-in-cancellation"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/events/1/admission-logs"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void getEventAdmissionLogs_returnsBadRequestForInvalidEnumAndPaging() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/events/1/admission-logs").param("action", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("CHECK_IN")));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventAdmissionLogs(eq(1L), eq(null), eq(null), any(), eq(0), eq(0));

        mockMvc.perform(get("/events/1/admission-logs").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void checkIn_returnsBadRequestForInvalidRequest() throws Exception {
        authenticate(10L);
        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .checkIn(eq(1L), any(), any());

        mockMvc.perform(post("/events/1/admission-checkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    private AdmissionCheckInDtos.CheckInResponse checkInResponse() {
        return new AdmissionCheckInDtos.CheckInResponse(
            11L,
            1L,
            "event",
            AdmissionTicketStatus.USED,
            OffsetDateTime.parse("2026-08-08T10:00:00+09:00"),
            21L,
            AdmissionAction.CHECK_IN,
            AdmissionResult.SUCCESS,
            OffsetDateTime.parse("2026-08-08T10:00:00+09:00")
        );
    }

    private AdmissionCheckInDtos.CheckInCancellationResponse cancelResponse() {
        return new AdmissionCheckInDtos.CheckInCancellationResponse(
            11L,
            1L,
            "event",
            AdmissionTicketStatus.ISSUED,
            null,
            22L,
            AdmissionAction.CHECK_IN_CANCEL,
            AdmissionResult.SUCCESS,
            OffsetDateTime.parse("2026-08-08T10:05:00+09:00")
        );
    }

    private AdmissionCheckInDtos.LogListResponse logResponse() {
        return new AdmissionCheckInDtos.LogListResponse(
            21L,
            11L,
            AdmissionAction.CHECK_IN,
            AdmissionResult.SUCCESS,
            "A Gate",
            "staff",
            OffsetDateTime.parse("2026-08-08T10:00:00+09:00")
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
