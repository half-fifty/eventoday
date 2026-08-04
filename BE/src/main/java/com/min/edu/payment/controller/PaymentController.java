package com.min.edu.payment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.dto.response.PaymentDetailResponse;
import com.min.edu.payment.service.PaymentConfirmService;
import com.min.edu.payment.service.PaymentQueryService;
import com.min.edu.payment.service.PaymentWebhookService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmService paymentConfirmService;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentQueryService paymentQueryService;

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

    @PostMapping("/payments/webhooks/toss")
    public ApiResponse<Void> receiveTossWebhook(
            @RequestHeader(value = "tosspayments-webhook-transmission-id", required = false)
            String transmissionId,
            @RequestHeader(value = "tosspayments-webhook-transmission-time", required = false)
            String transmissionTime,
            @RequestHeader(value = "tosspayments-webhook-transmission-retried-count", required = false)
            String retriedCount,
            @Valid @RequestBody TossPaymentWebhookRequest request) {
        paymentWebhookService.handleTossWebhook(request);

        return ApiResponse.success();
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentDetailResponse> getPaymentDetail(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken,
            @PathVariable Long paymentId) {
        PaymentDetailResponse response = paymentQueryService.getPaymentDetail(
            principal == null ? null : principal.getMemberId(),
            orderAccessToken,
            paymentId
        );

        return ApiResponse.success(response);
    }
}
