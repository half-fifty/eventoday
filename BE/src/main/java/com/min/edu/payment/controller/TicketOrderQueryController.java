package com.min.edu.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.payment.dto.response.MyTicketOrderListResponse;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.service.TicketOrderQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TicketOrderQueryController {

    private final TicketOrderQueryService ticketOrderQueryService;

    @GetMapping("/members/me/ticket-orders")
    public ApiResponse<MyTicketOrderListResponse> getMyTicketOrders(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MyTicketOrderListResponse response = ticketOrderQueryService.getMyTicketOrders(
            principal == null ? null : principal.getMemberId(),
            page,
            size
        );

        return ApiResponse.success(response);
    }

    @GetMapping("/ticket-orders/{orderNo}")
    public ApiResponse<TicketOrderDetailResponse> getTicketOrderDetail(
            @PathVariable String orderNo,
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken) {
        TicketOrderDetailResponse response = ticketOrderQueryService.getTicketOrderDetail(
            orderNo,
            principal == null ? null : principal.getMemberId(),
            orderAccessToken
        );

        return ApiResponse.success(response);
    }
}
