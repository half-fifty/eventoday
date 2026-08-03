package com.min.edu.payment.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.dto.response.MyTicketOrderListResponse;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.dto.response.TicketOrderListItemResponse;
import com.min.edu.payment.service.TicketOrderQueryService;

class TicketOrderQueryControllerTest {

    private TicketOrderQueryService ticketOrderQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketOrderQueryService = org.mockito.Mockito.mock(TicketOrderQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TicketOrderQueryController(ticketOrderQueryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyTicketOrders_returnsOrdersForAuthenticatedMember() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, 0, 20))
            .willReturn(response(List.of(item()), 0, 20, 1, 1, true, true, false));

        mockMvc.perform(get("/members/me/ticket-orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.content[0].ticketOrderId").value(1))
            .andExpect(jsonPath("$.data.content[0].orderNo").value("EVT-20260803-000000000001"))
            .andExpect(jsonPath("$.data.content[0].eventId").value(100))
            .andExpect(jsonPath("$.data.content[0].eventName").value("Event"))
            .andExpect(jsonPath("$.data.content[0].quantity").value(2))
            .andExpect(jsonPath("$.data.content[0].paymentRequired").value(true))
            .andExpect(jsonPath("$.data.content[0].buyerName").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].buyerEmail").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].buyerPhone").doesNotExist())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(ticketOrderQueryService).getMyTicketOrders(10L, 0, 20);
    }

    @Test
    void getMyTicketOrders_usesApiContextPathWithoutV1() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, 0, 20))
            .willReturn(response(List.of(), 0, 20, 0, 0, true, true, true));

        mockMvc.perform(get("/api/members/me/ticket-orders").contextPath("/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getMyTicketOrders_doesNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/v1/members/me/ticket-orders"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(ticketOrderQueryService);
    }

    @Test
    void getMyTicketOrders_returnsUnauthorizedWhenPrincipalIsMissing() throws Exception {
        given(ticketOrderQueryService.getMyTicketOrders(null, 0, 20))
            .willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/members/me/ticket-orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.UNAUTHORIZED.getCode()));

        verify(ticketOrderQueryService).getMyTicketOrders(null, 0, 20);
    }

    @Test
    void getMyTicketOrders_passesPageAndSize() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, 2, 5))
            .willReturn(response(List.of(), 2, 5, 12, 3, false, true, true));

        mockMvc.perform(get("/members/me/ticket-orders")
                .param("page", "2")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(5));

        verify(ticketOrderQueryService).getMyTicketOrders(10L, 2, 5);
    }

    @Test
    void getMyTicketOrders_returnsBadRequestWhenPageIsInvalid() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, -1, 20))
            .willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/members/me/ticket-orders").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void getMyTicketOrders_returnsBadRequestWhenSizeIsInvalid() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, 0, 101))
            .willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/members/me/ticket-orders").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    void getMyTicketOrders_returnsEmptyList() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getMyTicketOrders(10L, 0, 20))
            .willReturn(response(List.of(), 0, 20, 0, 0, true, true, true));

        mockMvc.perform(get("/members/me/ticket-orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0))
            .andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    void getTicketOrderDetail_returnsMemberOrder() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 10L, null))
            .willReturn(detailResponse());

        mockMvc.perform(get("/ticket-orders/ORDER-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"))
            .andExpect(jsonPath("$.data.ticketOrderId").value(1))
            .andExpect(jsonPath("$.data.eventName").value("Event"))
            .andExpect(jsonPath("$.data.exchangeCodes").isArray())
            .andExpect(jsonPath("$.data.buyerName").doesNotExist())
            .andExpect(jsonPath("$.data.buyerEmail").doesNotExist())
            .andExpect(jsonPath("$.data.buyerPhone").doesNotExist());

        verify(ticketOrderQueryService).getTicketOrderDetail("ORDER-1", 10L, null);
    }

    @Test
    void getTicketOrderDetail_usesApiContextPathWithoutV1() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 10L, null))
            .willReturn(detailResponse());

        mockMvc.perform(get("/api/ticket-orders/ORDER-1").contextPath("/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"));
    }

    @Test
    void getTicketOrderDetail_doesNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/v" + "1/ticket-orders/ORDER-1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getTicketOrderDetail_passesGuestTokenHeader() throws Exception {
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", null, "guest-token"))
            .willReturn(detailResponse());

        mockMvc.perform(get("/ticket-orders/ORDER-1")
                .header("X-Order-Access-Token", "guest-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("ORDER-1"));

        verify(ticketOrderQueryService)
            .getTicketOrderDetail("ORDER-1", null, "guest-token");
    }

    @Test
    void getTicketOrderDetail_returnsUnauthorized() throws Exception {
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", null, null))
            .willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/ticket-orders/ORDER-1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void getTicketOrderDetail_returnsForbidden() throws Exception {
        authenticate(20L);
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 20L, null))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED));

        mockMvc.perform(get("/ticket-orders/ORDER-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ORDER_ACCESS_DENIED.getCode()));
    }

    @Test
    void getTicketOrderDetail_returnsNotFound() throws Exception {
        authenticate(10L);
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 10L, null))
            .willThrow(new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND));

        mockMvc.perform(get("/ticket-orders/ORDER-1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.TICKET_ORDER_NOT_FOUND.getCode()));
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

    private MyTicketOrderListResponse response(
            List<TicketOrderListItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            boolean empty) {
        return new MyTicketOrderListResponse(
            content,
            page,
            size,
            totalElements,
            totalPages,
            first,
            last,
            empty
        );
    }

    private TicketOrderListItemResponse item() {
        return new TicketOrderListItemResponse(
            1L,
            "EVT-20260803-000000000001",
            100L,
            "Event",
            2,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            true,
            "PENDING",
            "PENDING_PAYMENT",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00"),
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00")
        );
    }

    private TicketOrderDetailResponse detailResponse() {
        return new TicketOrderDetailResponse(
            1L,
            "ORDER-1",
            100L,
            "Event",
            2,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            true,
            "PENDING",
            "PENDING_PAYMENT",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00"),
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            List.of()
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
