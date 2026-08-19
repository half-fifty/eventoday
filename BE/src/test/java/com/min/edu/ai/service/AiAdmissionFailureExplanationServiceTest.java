package com.min.edu.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.service.AdmissionFailureEligibilityQueryService;
import com.min.edu.admission.service.AdmissionFailureEligibilityView;
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
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAdmissionFailureExplanationServiceTest {

    private final AdmissionFailureEligibilityQueryService queryService =
        org.mockito.Mockito.mock(AdmissionFailureEligibilityQueryService.class);
    private final PromptProvider promptProvider = org.mockito.Mockito.mock(PromptProvider.class);
    private final AiModelGateway aiModelGateway = org.mockito.Mockito.mock(AiModelGateway.class);
    private final PolicyRetrievalService policyRetrievalService =
        org.mockito.Mockito.mock(PolicyRetrievalService.class);
    private final AiAdmissionFailureExplanationService service =
        new AiAdmissionFailureExplanationService(
            queryService,
            promptProvider,
            aiModelGateway,
            policyRetrievalService
        );

    @Test
    void sendsAdmissionEligibilityContextWithoutQrToken() {
        given(queryService.evaluateForMember(10L, 11L)).willReturn(view(
            AdmissionEligibilityReasonCode.ALREADY_USED
        ));
        given(promptProvider.get(PromptType.ADMISSION_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.empty());
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willReturn(new AiFailureExplanationOutput("AI explanation", "Ask staff", false));

        AiFailureExplanationResponse response = service.explainForMember(
            10L,
            11L,
            new AiFailureExplanationRequest("왜 입장이 안 되나요?")
        );

        assertThat(response.aiGenerated()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("ALREADY_USED");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(captor.capture(), eq(AiFailureExplanationOutput.class));
        assertThat(captor.getValue().tools()).isEmpty();
        assertThat(captor.getValue().userPrompt())
            .contains("[BACKEND_DECISION]")
            .contains("[CURRENT_STATE]")
            .contains("[POLICY_CONTEXT]")
            .contains("[USER_QUESTION]")
            .contains("ALREADY_USED")
            .contains("USED")
            .doesNotContain("qr-token")
            .doesNotContain("orderAccessToken");
    }

    @Test
    void includesRetrievedAdmissionPolicyContextInGatewayPrompt() {
        given(queryService.evaluateForMember(10L, 11L)).willReturn(view(
            AdmissionEligibilityReasonCode.ALREADY_USED
        ));
        given(promptProvider.get(PromptType.ADMISSION_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.from("이미 사용 처리된 입장권은 중복 입장에 사용할 수 없습니다.", 1));
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willReturn(new AiFailureExplanationOutput("AI explanation", "Ask staff", false));

        service.explainForMember(10L, 11L, new AiFailureExplanationRequest("왜 안 되나요?"));

        ArgumentCaptor<PolicyRetrievalRequest> retrievalCaptor =
            ArgumentCaptor.forClass(PolicyRetrievalRequest.class);
        verify(policyRetrievalService).retrieve(retrievalCaptor.capture());
        assertThat(retrievalCaptor.getValue().policyType().name()).isEqualTo("ADMISSION");
        assertThat(retrievalCaptor.getValue().reasonCode()).isEqualTo("ALREADY_USED");

        ArgumentCaptor<AiChatRequest> chatCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(chatCaptor.capture(), eq(AiFailureExplanationOutput.class));
        assertThat(chatCaptor.getValue().userPrompt())
            .contains("이미 사용 처리된 입장권은 중복 입장에 사용할 수 없습니다.")
            .contains("eligible: false")
            .contains("reasonCode: ALREADY_USED");
    }

    @Test
    void returnsFallbackForInvalidAiResponse() {
        given(queryService.evaluateForMember(10L, 11L)).willReturn(view(
            AdmissionEligibilityReasonCode.TICKET_NOT_ISSUED
        ));
        given(promptProvider.get(PromptType.ADMISSION_FAILURE_EXPLANATION)).willReturn("system");
        given(policyRetrievalService.retrieve(any(PolicyRetrievalRequest.class)))
            .willReturn(PolicyRetrievalResult.empty());
        given(aiModelGateway.chat(any(AiChatRequest.class), eq(AiFailureExplanationOutput.class)))
            .willThrow(new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID));

        AiFailureExplanationResponse response = service.explainForMember(
            10L,
            11L,
            new AiFailureExplanationRequest("왜요?")
        );

        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.explanation()).contains("입장권이 발급 완료 상태");
        assertThat(response.reasonCode()).isEqualTo("TICKET_NOT_ISSUED");
    }

    @Test
    void doesNotHideNotFoundAsFallback() {
        given(queryService.evaluateForGuest("ORDER-1", "bad", 11L))
            .willThrow(new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));

        assertThatThrownBy(() -> service.explainForGuest(
            "ORDER-1",
            "bad",
            11L,
            new AiFailureExplanationRequest("왜요?")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);
    }

    private AdmissionFailureEligibilityView view(AdmissionEligibilityReasonCode reasonCode) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return new AdmissionFailureEligibilityView(
            11L,
            3L,
            "event",
            new AdmissionEligibilityResult(
                reasonCode == AdmissionEligibilityReasonCode.ELIGIBLE,
                reasonCode,
                reasonCode == AdmissionEligibilityReasonCode.ALREADY_USED
                    ? AdmissionTicketStatus.USED
                    : AdmissionTicketStatus.EXPIRED,
                EventStatus.PUBLISHED,
                now.plusHours(3),
                reasonCode == AdmissionEligibilityReasonCode.ALREADY_USED ? now.minusHours(1) : null
            )
        );
    }
}
