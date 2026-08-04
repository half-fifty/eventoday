package com.min.edu.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.service.PaymentConfirmService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmService paymentConfirmService;

    @PostMapping("/payments/confirm")
    public ApiResponse<ConfirmPaymentResponse> confirmPayment(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        ConfirmPaymentResponse response = paymentConfirmService.confirm(
            principal == null ? null : principal.getMemberId(),
            orderAccessToken,
            request
        );

        return ApiResponse.success(response);
    }
}
