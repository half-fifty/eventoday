package com.min.edu.payment.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.service.TicketOrderService;

class TicketOrderControllerTest {

    private TicketOrderService ticketOrderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketOrderService = org.mockito.Mockito.mock(TicketOrderService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TicketOrderController(ticketOrderService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTicketOrder_createsGuestOrder() throws Exception {
        given(ticketOrderService.create(
                eq(1L),
                eq(null),
                org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)))
            .willReturn(response(false));

        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("""
                    {
                      "quantity": 2,
                      "buyer": {
                        "name": "guest",
                        "email": "guest@example.com",
                        "phone": "010-1234-5678"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.paymentRequired").value(false));

        verify(ticketOrderService).create(
            eq(1L),
            eq(null),
            org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)
        );
    }

    @Test
    void createTicketOrder_createsMemberOrder() throws Exception {
        AuthenticatedMemberDto principal =
            new AuthenticatedMemberDto(10L, PlatformRole.USER);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

        given(ticketOrderService.create(
                eq(1L),
                eq(10L),
                org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)))
            .willReturn(response(true));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("""
                    {
                      "quantity": 2
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentRequired").value(true));

        verify(ticketOrderService).create(
            eq(1L),
            eq(10L),
            org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)
        );
    }

    @Test
    void createTicketOrder_returnsBadRequestWhenQuantityIsMissing() throws Exception {
        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void createTicketOrder_returnsBadRequestWhenQuantityIsZero() throws Exception {
        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("""
                    {
                      "quantity": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void createTicketOrder_returnsBusinessErrorWhenGuestBuyerIsMissing() throws Exception {
        given(ticketOrderService.create(
                eq(1L),
                eq(null),
                org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)))
            .willThrow(new BusinessException(GlobalErrorCode.INVALID_GUEST_BUYER_INFO));

        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("""
                    {
                      "quantity": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_GUEST_BUYER_INFO.getCode()));
    }

    @Test
    void createTicketOrder_returnsEventNotFound() throws Exception {
        given(ticketOrderService.create(
                eq(1L),
                eq(null),
                org.mockito.ArgumentMatchers.any(CreateTicketOrderRequest.class)))
            .willThrow(new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));

        mockMvc.perform(post("/v1/events/1/ticket-orders")
                .contentType("application/json")
                .content("""
                    {
                      "quantity": 1,
                      "buyer": {
                        "name": "guest",
                        "email": "guest@example.com",
                        "phone": "010-1234-5678"
                      }
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.EVENT_NOT_FOUND.getCode()));
    }

    private CreateTicketOrderResponse response(boolean paymentRequired) {
        return new CreateTicketOrderResponse(
            "EVT-20260803-A81C29F4307B",
            100L,
            2,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            paymentRequired,
            paymentRequired ? "PENDING" : null,
            paymentRequired ? null : "CONFIRMED",
            null,
            null
        );
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
