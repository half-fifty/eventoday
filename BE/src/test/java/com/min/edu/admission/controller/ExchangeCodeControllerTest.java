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

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.dto.ExchangeCodeRedemptionDtos;
import com.min.edu.admission.service.ExchangeCodeQueryService;
import com.min.edu.admission.service.ExchangeCodeRedemptionService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ExchangeCodeControllerTest {

    private ExchangeCodeQueryService service;
    private ExchangeCodeRedemptionService redemptionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ExchangeCodeQueryService.class);
        redemptionService = org.mockito.Mockito.mock(ExchangeCodeRedemptionService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ExchangeCodeController(service, redemptionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getEventExchangeCodes_returnsMaskedCodesWithoutRawCode() throws Exception {
        authenticate(10L);
        given(service.getEventExchangeCodes(
            eq(1L),
            eq(ExchangeCodeStatus.ISSUED),
            any(AuthenticatedMemberDto.class),
            eq(1),
            eq(10)))
            .willReturn(new PageImpl<>(
                List.of(eventResponse()),
                PageRequest.of(1, 10),
                1
            ));

        mockMvc.perform(get("/events/1/exchange-codes")
                .param("status", "ISSUED")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.content[0].exchangeCodeId").value(7))
            .andExpect(jsonPath("$.data.content[0].maskedCode").value("A13F************3FCD"))
            .andExpect(jsonPath("$.data.content[0].source").value("TICKET_ORDER"))
            .andExpect(jsonPath("$.data.content[0].holderNickname").value("holder"))
            .andExpect(jsonPath("$.data.content[0].code").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].holderMemberId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].ticketOrderId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].exchangeCodeRequestId").doesNotExist());

        verify(service).getEventExchangeCodes(
            eq(1L),
            eq(ExchangeCodeStatus.ISSUED),
            any(AuthenticatedMemberDto.class),
            eq(1),
            eq(10)
        );
    }

    @Test
    void getEventExchangeCodes_usesApiContextPathWithoutV1() throws Exception {
        authenticate(10L);
        given(service.getEventExchangeCodes(eq(1L), eq(null), any(), eq(0), eq(20)))
            .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/events/1/exchange-codes").contextPath("/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getEventExchangeCodes_doesNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/v1/events/1/exchange-codes"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void getEventExchangeCodes_returnsBadRequestWhenStatusIsInvalid() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/events/1/exchange-codes")
                .param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("status")))
            .andExpect(jsonPath("$.message").value(containsString("ISSUED")))
            .andExpect(jsonPath("$.message").value(containsString("REDEEMED")))
            .andExpect(jsonPath("$.message").value(containsString("CANCELLED")))
            .andExpect(jsonPath("$.message").value(containsString("EXPIRED")));

        verifyNoInteractions(service);
    }

    @Test
    void getEventExchangeCodes_returnsBadRequestForInvalidPageOrSize() throws Exception {
        authenticate(10L);
        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventExchangeCodes(eq(1L), eq(null), any(), eq(-1), eq(20));

        mockMvc.perform(get("/events/1/exchange-codes").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventExchangeCodes(eq(1L), eq(null), any(), eq(0), eq(0));

        mockMvc.perform(get("/events/1/exchange-codes").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventExchangeCodes(eq(1L), eq(null), any(), eq(0), eq(101));

        mockMvc.perform(get("/events/1/exchange-codes").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void getMyExchangeCodes_returnsOwnRawCodes() throws Exception {
        authenticate(10L);
        given(service.getMyExchangeCodes(
            eq(ExchangeCodeStatus.REDEEMED),
            any(AuthenticatedMemberDto.class),
            eq(0),
            eq(20)))
            .willReturn(new PageImpl<>(
                List.of(myResponse()),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/members/me/exchange-codes")
                .param("status", "REDEEMED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].exchangeCodeId").value(7))
            .andExpect(jsonPath("$.data.content[0].code").value("A13FC9-12AA81-093FCD"))
            .andExpect(jsonPath("$.data.content[0].source").value("EXTERNAL_REQUEST"))
            .andExpect(jsonPath("$.data.content[0].maskedCode").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].holderMemberId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].holderNickname").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].ticketOrderId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].exchangeCodeRequestId").doesNotExist());

        verify(service).getMyExchangeCodes(
            eq(ExchangeCodeStatus.REDEEMED),
            any(AuthenticatedMemberDto.class),
            eq(0),
            eq(20)
        );
    }

    @Test
    void getMyExchangeCodes_returnsBadRequestWhenStatusIsInvalid() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/members/me/exchange-codes")
                .param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("status")))
            .andExpect(jsonPath("$.message").value(containsString("ISSUED")))
            .andExpect(jsonPath("$.message").value(containsString("REDEEMED")))
            .andExpect(jsonPath("$.message").value(containsString("CANCELLED")))
            .andExpect(jsonPath("$.message").value(containsString("EXPIRED")));

        verifyNoInteractions(service);
    }

    @Test
    void getMyExchangeCodes_returnsBadRequestForInvalidPageOrSize() throws Exception {
        authenticate(10L);
        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getMyExchangeCodes(eq(null), any(), eq(-1), eq(20));

        mockMvc.perform(get("/members/me/exchange-codes").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getMyExchangeCodes(eq(null), any(), eq(0), eq(0));

        mockMvc.perform(get("/members/me/exchange-codes").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getMyExchangeCodes(eq(null), any(), eq(0), eq(101));

        mockMvc.perform(get("/members/me/exchange-codes").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void getMyExchangeCodes_returnsUnauthorizedWhenPrincipalIsMissing() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED))
            .given(service)
            .getMyExchangeCodes(eq(null), eq(null), eq(0), eq(20));

        mockMvc.perform(get("/members/me/exchange-codes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.UNAUTHORIZED.getCode()));

        verify(service).getMyExchangeCodes(null, null, 0, 20);
    }

    @Test
    void getMyExchangeCodes_usesApiContextPathWithoutV1() throws Exception {
        authenticate(10L);
        given(service.getMyExchangeCodes(eq(null), any(), eq(0), eq(20)))
            .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/members/me/exchange-codes").contextPath("/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getMyExchangeCodes_doesNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/v1/members/me/exchange-codes"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void validateExchangeCode_returnsValidationResponse() throws Exception {
        authenticate(10L);
        given(redemptionService.validate(any(), any(AuthenticatedMemberDto.class)))
            .willReturn(new ExchangeCodeRedemptionDtos.ValidationResponse(
                true,
                1L,
                "event",
                ExchangeCodeDtos.Source.EXTERNAL_REQUEST,
                ExchangeCodeStatus.ISSUED,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ));

        mockMvc.perform(post("/exchange-codes/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"A13FC9-12AA81-093FCD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.eventId").value(1))
            .andExpect(jsonPath("$.data.source").value("EXTERNAL_REQUEST"))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.code").doesNotExist());

        verify(redemptionService).validate(any(), any(AuthenticatedMemberDto.class));
    }

    @Test
    void redeemExchangeCode_returnsAdmissionTicketSummaryWithoutQrToken() throws Exception {
        authenticate(10L);
        given(redemptionService.redeem(any(), any(AuthenticatedMemberDto.class)))
            .willReturn(new ExchangeCodeRedemptionDtos.RedemptionResponse(
                7L,
                ExchangeCodeStatus.REDEEMED,
                11L,
                1L,
                "event",
                AdmissionTicketStatus.ISSUED,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
                true
            ));

        mockMvc.perform(post("/exchange-codes/redemption")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"A13FC9-12AA81-093FCD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exchangeCodeId").value(7))
            .andExpect(jsonPath("$.data.exchangeCodeStatus").value("REDEEMED"))
            .andExpect(jsonPath("$.data.admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.admissionTicketStatus").value("ISSUED"))
            .andExpect(jsonPath("$.data.qrAvailable").value(true))
            .andExpect(jsonPath("$.data.qrToken").doesNotExist())
            .andExpect(jsonPath("$.data.code").doesNotExist());

        verify(redemptionService).redeem(any(), any(AuthenticatedMemberDto.class));
    }

    @Test
    void exchangeCodeRedemptionApis_rejectBlankCode() throws Exception {
        authenticate(10L);

        mockMvc.perform(post("/exchange-codes/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        mockMvc.perform(post("/exchange-codes/redemption")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(redemptionService);
    }

    @Test
    void exchangeCodeRedemptionApis_doNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(post("/v1/exchange-codes/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"A13FC9-12AA81-093FCD\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/exchange-codes/redemption")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"A13FC9-12AA81-093FCD\"}"))
            .andExpect(status().isNotFound());
    }

    private ExchangeCodeDtos.EventListResponse eventResponse() {
        return new ExchangeCodeDtos.EventListResponse(
            7L,
            1L,
            "event",
            "A13F************3FCD",
            ExchangeCodeDtos.Source.TICKET_ORDER,
            ExchangeCodeStatus.ISSUED,
            "holder",
            null,
            null,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
        );
    }

    private ExchangeCodeDtos.MyListResponse myResponse() {
        return new ExchangeCodeDtos.MyListResponse(
            7L,
            1L,
            "event",
            "A13FC9-12AA81-093FCD",
            ExchangeCodeDtos.Source.EXTERNAL_REQUEST,
            ExchangeCodeStatus.REDEEMED,
            null,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-06T09:00:00+09:00")
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

                return SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();
            }
        };
    }
}
