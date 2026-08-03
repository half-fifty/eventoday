package com.min.edu.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.service.TicketOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class TicketOrderController {

    private final TicketOrderService ticketOrderService;

    @PostMapping("/{eventId}/ticket-orders")
    public ApiResponse<CreateTicketOrderResponse> createTicketOrder(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @Valid @RequestBody CreateTicketOrderRequest request) {
        CreateTicketOrderResponse response = ticketOrderService.create(
            eventId,
            principal == null ? null : principal.getMemberId(),
            request
        );

        return ApiResponse.success(response);
    }
}
