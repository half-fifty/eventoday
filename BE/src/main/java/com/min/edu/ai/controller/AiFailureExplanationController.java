package com.min.edu.ai.controller;

import com.min.edu.ai.dto.AiFailureExplanationRequest;
import com.min.edu.ai.dto.AiFailureExplanationResponse;
import com.min.edu.ai.service.AiAdmissionFailureExplanationService;
import com.min.edu.ai.service.AiRefundFailureExplanationService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiFailureExplanationController {

    private final AiRefundFailureExplanationService refundExplanationService;
    private final AiAdmissionFailureExplanationService admissionExplanationService;

    public AiFailureExplanationController(
            AiRefundFailureExplanationService refundExplanationService,
            AiAdmissionFailureExplanationService admissionExplanationService) {
        this.refundExplanationService = refundExplanationService;
        this.admissionExplanationService = admissionExplanationService;
    }

    @PostMapping("/payments/{paymentId}/refunds/ai-explanation")
    public ApiResponse<AiFailureExplanationResponse> explainRefundFailure(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken,
            @PathVariable Long paymentId,
            @Valid @RequestBody AiFailureExplanationRequest request) {
        return ApiResponse.success(refundExplanationService.explain(
            principal == null ? null : principal.getMemberId(),
            orderAccessToken,
            paymentId,
            request
        ));
    }

    @PostMapping("/members/me/admission-tickets/{admissionTicketId}/ai-failure-explanation")
    public ApiResponse<AiFailureExplanationResponse> explainMyAdmissionFailure(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @PathVariable Long admissionTicketId,
            @Valid @RequestBody AiFailureExplanationRequest request) {
        return ApiResponse.success(admissionExplanationService.explainForMember(
            principal == null ? null : principal.getMemberId(),
            admissionTicketId,
            request
        ));
    }

    @PostMapping("/ticket-orders/{orderNo}/admission-tickets/{admissionTicketId}/ai-failure-explanation")
    public ApiResponse<AiFailureExplanationResponse> explainGuestAdmissionFailure(
            @PathVariable String orderNo,
            @PathVariable Long admissionTicketId,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken,
            @Valid @RequestBody AiFailureExplanationRequest request) {
        return ApiResponse.success(admissionExplanationService.explainForGuest(
            orderNo,
            orderAccessToken,
            admissionTicketId,
            request
        ));
    }
}
