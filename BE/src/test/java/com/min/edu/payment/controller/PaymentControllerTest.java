package com.min.edu.payment.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.dto.response.PaymentDetailResponse;
import com.min.edu.payment.service.PaymentQueryService;
import com.min.edu.payment.service.PaymentConfirmService;
import com.min.edu.payment.service.PaymentWebhookService;

class PaymentControllerTest {

    private PaymentConfirmService paymentConfirmService;
    private PaymentWebhookService paymentWebhookService;
    private PaymentQueryService paymentQueryService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        paymentConfirmService = org.mockito.Mockito.mock(PaymentConfirmService.class);
        paymentWebhookService = org.mockito.Mockito.mock(PaymentWebhookService.class);
        paymentQueryService = org.mockito.Mockito.mock(PaymentQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentController(
                paymentConfirmService,
                paymentWebhookService,
                paymentQueryService
            ))
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
    void confirmPayment_mapsWithoutV1() throws Exception {
        authenticate(10L);
        ConfirmPaymentRequest request = request();
        given(paymentConfirmService.confirm(eq(10L), eq(null), org.mockito.ArgumentMatchers.any()))
            .willReturn(response());

        mockMvc.perform(post("/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.paymentId").value(1))
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"))
            .andExpect(jsonPath("$.data.ticketOrderId").value(2))
            .andExpect(jsonPath("$.data.amount").value(10000))
            .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.data.ticketOrderStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.paymentKey").doesNotExist())
            .andExpect(jsonPath("$.data.exchangeCodes").doesNotExist());
    }

    @Test
    void confirmPayment_usesApiContextPathWithoutV1() throws Exception {
        authenticate(10L);
        given(paymentConfirmService.confirm(eq(10L), eq(null), org.mockito.ArgumentMatchers.any()))
            .willReturn(response());

        mockMvc.perform(post("/api/payments/confirm")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"));
    }

    @Test
    void confirmPayment_doesNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(post("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isNotFound());

        verifyNoInteractions(paymentConfirmService);
    }

    @Test
    void confirmPayment_passesGuestHeader() throws Exception {
        given(paymentConfirmService.confirm(eq(null), eq("token"), org.mockito.ArgumentMatchers.any()))
            .willReturn(response());

        mockMvc.perform(post("/payments/confirm")
                .header("X-Order-Access-Token", "token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk());

        verify(paymentConfirmService)
            .confirm(eq(null), eq("token"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmPayment_returnsBadRequestForInvalidBody() throws Exception {
        mockMvc.perform(post("/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"paymentKey":"","orderId":"ORDER-1","amount":10000}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void confirmPayment_mapsProcessingConflict() throws Exception {
        authenticate(10L);
        given(paymentConfirmService.confirm(eq(10L), eq(null), org.mockito.ArgumentMatchers.any()))
            .willThrow(new BusinessException(GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT));

        mockMvc.perform(post("/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_409_003"))
            .andExpect(jsonPath("$.message").value(
                GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT.getMessage()
            ));
    }

    @Test
    void confirmPayment_mapsTossTimeout() throws Exception {
        authenticate(10L);
        given(paymentConfirmService.confirm(eq(10L), eq(null), org.mockito.ArgumentMatchers.any()))
            .willThrow(new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        mockMvc.perform(post("/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value("PAYMENT_504_001"));
    }

    @Test
    void receiveTossWebhook_mapsWithoutV1AndDoesNotExposePaymentKey() throws Exception {
        mockMvc.perform(post("/payments/webhooks/toss")
                .header("tosspayments-webhook-transmission-id", "transmission-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventType":"PAYMENT_STATUS_CHANGED",
                      "createdAt":"2026-08-04T11:20:00.123456",
                      "data":{
                        "paymentKey":"payment-key",
                        "orderId":"ORDER-1",
                        "totalAmount":10000,
                        "status":"DONE",
                        "method":"CARD",
                        "requestedAt":"2026-08-03T10:00:00+09:00",
                        "approvedAt":"2026-08-03T10:01:00+09:00"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.paymentKey").doesNotExist());

        ArgumentCaptor<TossPaymentWebhookRequest> captor =
            ArgumentCaptor.forClass(TossPaymentWebhookRequest.class);
        verify(paymentWebhookService).handleTossWebhook(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCreatedAt())
            .isEqualTo("2026-08-04T11:20:00.123456");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getData().getApprovedAt())
            .isEqualTo(OffsetDateTime.parse("2026-08-03T10:01:00+09:00"));
    }

    @Test
    void receiveTossWebhook_doesNotExposeV1Path() throws Exception {
        mockMvc.perform(post("/v1/payments/webhooks/toss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void receiveTossWebhook_returnsBadRequestForInvalidBody() throws Exception {
        mockMvc.perform(post("/payments/webhooks/toss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventType":"PAYMENT_STATUS_CHANGED","data":{}}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void receiveTossWebhook_returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/payments/webhooks/toss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventType":"PAYMENT_STATUS_CHANGED",
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(GlobalErrorCode.INVALID_INPUT_VALUE.getMessage()))
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jackson"))
            ));
    }

    @Test
    void receiveTossWebhook_returnsBadRequestForWrongFieldType() throws Exception {
        mockMvc.perform(post("/payments/webhooks/toss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventType":"PAYMENT_STATUS_CHANGED",
                      "createdAt":"2026-08-04T11:20:00.123456",
                      "data":{
                        "paymentKey":"payment-key",
                        "orderId":"ORDER-1",
                        "totalAmount":"not-a-number",
                        "status":"DONE"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void getPaymentDetail_mapsWithoutV1AndDoesNotExposeSensitiveFields() throws Exception {
        authenticate(10L);
        given(paymentQueryService.getPaymentDetail(eq(10L), eq(null), eq(1L)))
            .willReturn(paymentDetailResponse());

        mockMvc.perform(get("/payments/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentId").value(1))
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"))
            .andExpect(jsonPath("$.data.ticketOrderId").value(2))
            .andExpect(jsonPath("$.data.eventName").value("event name"))
            .andExpect(jsonPath("$.data.amount").value(10000))
            .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.data.paymentKey").doesNotExist())
            .andExpect(jsonPath("$.data.buyerEmail").doesNotExist())
            .andExpect(jsonPath("$.data.exchangeCodes").doesNotExist());
    }

    @Test
    void getPaymentDetail_passesGuestTokenHeader() throws Exception {
        given(paymentQueryService.getPaymentDetail(eq(null), eq("token"), eq(1L)))
            .willReturn(paymentDetailResponse());

        mockMvc.perform(get("/payments/1")
                .header("X-Order-Access-Token", "token"))
            .andExpect(status().isOk());

        verify(paymentQueryService).getPaymentDetail(eq(null), eq("token"), eq(1L));
    }

    @Test
    void getPaymentDetail_doesNotExposeV1Path() throws Exception {
        mockMvc.perform(get("/v1/payments/1"))
            .andExpect(status().isNotFound());
    }

    private ConfirmPaymentRequest request() {
        return new ConfirmPaymentRequest(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000)
        );
    }

    private ConfirmPaymentResponse response() {
        return new ConfirmPaymentResponse(
            1L,
            "ORDER-1",
            2L,
            BigDecimal.valueOf(10000),
            "PAID",
            "CONFIRMED",
            OffsetDateTime.parse("2026-08-03T10:01:00+09:00")
        );
    }

    private PaymentDetailResponse paymentDetailResponse() {
        return new PaymentDetailResponse(
            1L,
            "ORDER-1",
            2L,
            3L,
            "event name",
            "TOSS_PAYMENTS",
            "CARD",
            BigDecimal.valueOf(10000),
            "PAID",
            "CONFIRMED",
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-03T10:01:00+09:00")
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
