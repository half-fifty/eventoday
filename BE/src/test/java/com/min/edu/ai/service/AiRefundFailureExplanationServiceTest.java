package com.min.edu.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiFailureExplanationOutput;
import com.min.edu.ai.dto.AiFailureExplanationRequest;
import com.min.edu.ai.dto.AiFailureExplanationResponse;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.ai.rag.PolicyRetrievalRequest;
import com.min.edu.ai.rag.PolicyRetrievalResult;
import com.min.edu.ai.rag.PolicyRetrievalService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.policy.RefundEligibilityReasonCode;
import com.min.edu.payment.policy.RefundEligibilityResult;
import com.min.edu.payment.service.RefundEligibilityQueryService;
import com.min.edu.payment.service.RefundEligibilityView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiRefundFailureExplanationServiceTest {

    private final RefundEligibilityQueryService queryService =
        org.mockito.Mockito.mock(RefundEligibilityQueryService.class);
    private final PromptProvider promptProvider = org.mockito.Mockito.mock(PromptProvider.class);
    private final AiModelGateway aiModelGateway = org.mockito.Mockito.mock(AiModelGateway.class);
    private final PolicyRetrievalService policyRetrievalService =
        org.mockito.Mockito.mock(PolicyRetrievalService.class);
    private final AiRefundFailureExplanationService service =
        new AiRefundFailureExplanationService(
            queryService,
            promptProvider,
            aiModelGateway,
            policyRetrievalService
        );

    @Test
    void sendsDeterministicSafeContextToGatewayAndReturnsAiResponse() {
        given(queryService.evaluate(10L, null, 1L)).willReturn(view(
            RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED
        ));
        given(promptProvider.get(PromptType.REFUND_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.empty());
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willReturn(new AiFailureExplanationOutput("AI explanation", "Ask staff", false));

        AiFailureExplanationResponse response = service.explain(
            10L,
            null,
            1L,
            new AiFailureExplanationRequest("왜 환불이 안 되나요?")
        );

        assertThat(response.aiGenerated()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("EXCHANGE_CODE_ALREADY_REDEEMED");
        assertThat(response.explanation()).isEqualTo("AI explanation");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(captor.capture(), eq(AiFailureExplanationOutput.class));
        assertThat(captor.getValue().tools()).isEmpty();
        assertThat(captor.getValue().userPrompt())
            .contains("[BACKEND_DECISION]")
            .contains("[CURRENT_STATE]")
            .contains("[POLICY_CONTEXT]")
            .contains("[USER_QUESTION]")
            .contains("EXCHANGE_CODE_ALREADY_REDEEMED")
            .contains("exchangeCodeRedeemed")
            .doesNotContain("payment-key")
            .doesNotContain("orderAccessToken")
            .doesNotContain("accountNumber")
            .doesNotContain("holderName")
            .doesNotContain("guest@example.com")
            .doesNotContain("010-1234-5678");
    }

    @Test
    void includesRetrievedRefundPolicyContextInGatewayPrompt() {
        given(queryService.evaluate(10L, null, 1L)).willReturn(view(
            RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED
        ));
        given(promptProvider.get(PromptType.REFUND_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.from("교환 코드가 이미 사용된 주문은 환불할 수 없습니다.", 1));
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willReturn(new AiFailureExplanationOutput("AI explanation", "Ask staff", false));

        service.explain(10L, null, 1L, new AiFailureExplanationRequest("왜 안 되나요?"));

        ArgumentCaptor<PolicyRetrievalRequest> retrievalCaptor =
            ArgumentCaptor.forClass(PolicyRetrievalRequest.class);
        verify(policyRetrievalService).retrieve(retrievalCaptor.capture());
        assertThat(retrievalCaptor.getValue().policyType().name()).isEqualTo("REFUND");
        assertThat(retrievalCaptor.getValue().reasonCode())
            .isEqualTo("EXCHANGE_CODE_ALREADY_REDEEMED");

        ArgumentCaptor<AiChatRequest> chatCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(chatCaptor.capture(), eq(AiFailureExplanationOutput.class));
        assertThat(chatCaptor.getValue().userPrompt())
            .contains("교환 코드가 이미 사용된 주문은 환불할 수 없습니다.")
            .contains("refundable: false")
            .contains("reasonCode: EXCHANGE_CODE_ALREADY_REDEEMED");
    }

    @Test
    void returnsDeterministicFallbackForAiProviderFailures() {
        given(queryService.evaluate(10L, null, 1L)).willReturn(view(
            RefundEligibilityReasonCode.OPERATION_CUTOFF_PASSED
        ));
        given(promptProvider.get(PromptType.REFUND_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.from("policy", 1));
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willThrow(new BusinessException(GlobalErrorCode.AI_REQUEST_TIMEOUT));

        AiFailureExplanationResponse response = service.explain(
            10L,
            null,
            1L,
            new AiFailureExplanationRequest("왜요?")
        );

        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.needsHumanSupport()).isTrue();
        assertThat(response.explanation()).contains("행사 운영 마감 시각");
        assertThat(response.reasonCode()).isEqualTo("OPERATION_CUTOFF_PASSED");
    }

    @Test
    void doesNotHideAuthorizationFailureAsFallback() {
        given(queryService.evaluate(20L, null, 1L))
            .willThrow(new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED));

        assertThatThrownBy(() -> service.explain(
            20L,
            null,
            1L,
            new AiFailureExplanationRequest("왜요?")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);
    }

    private RefundEligibilityView view(RefundEligibilityReasonCode reasonCode) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return new RefundEligibilityView(
            1L,
            2L,
            3L,
            "event",
            "CARD",
            "REQUESTED",
            BigDecimal.valueOf(10000),
            new RefundEligibilityResult(
                reasonCode == RefundEligibilityReasonCode.ELIGIBLE,
                reasonCode,
                "PAID",
                "PAID",
                "CONFIRMED",
                BigDecimal.valueOf(10000),
                now.plusHours(3),
                now.plusHours(2),
                reasonCode == RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED,
                now
            ),
            now
        );
    }
}
