package com.min.edu.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.service.AdmissionEligibilityQueryService;
import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiCategory;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;
import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.rag.PolicyRetrievalResult;
import com.min.edu.ai.rag.PolicyRetrievalService;
import com.min.edu.ai.tool.AdmissionEligibilityAiTool;
import com.min.edu.ai.tool.AdmissionTicketAiTool;
import com.min.edu.ai.tool.EventOperationInfoAiTool;
import com.min.edu.ai.tool.ExchangeCodeAiTool;
import com.min.edu.ai.tool.TicketOrderAiTool;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AiCopilotServiceTest {

    private final com.min.edu.ai.prompt.PromptProvider promptProvider =
        Mockito.mock(com.min.edu.ai.prompt.PromptProvider.class);
    private final AiModelGateway aiModelGateway = Mockito.mock(AiModelGateway.class);
    private final PolicyRetrievalService policyRetrievalService = Mockito.mock(PolicyRetrievalService.class);
    private final CopilotRetrievalQuerySanitizer retrievalQuerySanitizer =
        new CopilotRetrievalQuerySanitizer();
    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final AdmissionEligibilityQueryService admissionEligibilityQueryService =
        Mockito.mock(AdmissionEligibilityQueryService.class);
    private final TicketOrderAiTool ticketOrderAiTool = Mockito.mock(TicketOrderAiTool.class);
    private final AdmissionTicketAiTool admissionTicketAiTool = Mockito.mock(AdmissionTicketAiTool.class);
    private final AdmissionEligibilityAiTool admissionEligibilityAiTool =
        Mockito.mock(AdmissionEligibilityAiTool.class);
    private final ExchangeCodeAiTool exchangeCodeAiTool = Mockito.mock(ExchangeCodeAiTool.class);
    private final EventOperationInfoAiTool eventOperationInfoAiTool =
        Mockito.mock(EventOperationInfoAiTool.class);

    private final AiCopilotService service = new AiCopilotService(
        promptProvider,
        aiModelGateway,
        policyRetrievalService,
        retrievalQuerySanitizer,
        eventOperationAccessService,
        admissionEligibilityQueryService,
        ticketOrderAiTool,
        admissionTicketAiTool,
        admissionEligibilityAiTool,
        exchangeCodeAiTool,
        eventOperationInfoAiTool
    );

    @Test
    void verifiesEventAccessBuildsToolContextAndReturnsGatewayResponse() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(10L, PlatformRole.USER);
        given(promptProvider.get(com.min.edu.ai.prompt.PromptType.AI_COPILOT))
            .willReturn("system prompt");
        given(policyRetrievalService.retrieveForCopilot(anyString()))
            .willReturn(PolicyRetrievalResult.empty());
        AiCopilotResponse expected = new AiCopilotResponse(
            "The ticket is already used.",
            AiCategory.ADMISSION,
            false
        );
        given(aiModelGateway.chat(any(AiChatRequest.class))).willReturn(new AiChatResult(expected));

        AiCopilotResponse response = service.ask(
            100L,
            new AiCopilotRequest(
                "Why can this ticket not enter?",
                null,
                new AiCopilotRequest.Context(null, 1L, null, null)
            ),
            actor
        );

        assertThat(response).isEqualTo(expected);
        verify(eventOperationAccessService).requireOperationalAccess(100L, actor);
        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());
        AiChatRequest request = requestCaptor.getValue();
        assertThat(request.systemPrompt()).isEqualTo("system prompt");
        assertThat(request.toolContext().memberId()).isEqualTo(10L);
        assertThat(request.toolContext().eventId()).isEqualTo(100L);
        assertThat(request.tools()).hasSize(5);
        assertThat(request.userPrompt()).contains("admissionTicketId: 1");
        assertThat(request.userPrompt()).contains("[TRUSTED_POLICY_CONTEXT]", "NONE");
    }

    @Test
    void resolvesQrTokenBeforePromptAndDoesNotExposeRawToken() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(10L, PlatformRole.USER);
        given(promptProvider.get(com.min.edu.ai.prompt.PromptType.AI_COPILOT))
            .willReturn("system prompt");
        given(admissionEligibilityQueryService.resolveTicketIdByQrToken(100L, "secret-qr-token"))
            .willReturn(55L);
        given(policyRetrievalService.retrieveForCopilot(anyString()))
            .willReturn(PolicyRetrievalResult.empty());
        given(aiModelGateway.chat(any(AiChatRequest.class))).willReturn(new AiChatResult(
            new AiCopilotResponse("Check the ticket.", AiCategory.ADMISSION, false)
        ));

        service.ask(
            100L,
            new AiCopilotRequest(
                "Why does this QR fail?",
                null,
                new AiCopilotRequest.Context(null, null, null, "secret-qr-token")
            ),
            actor
        );

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(policyRetrievalService).retrieveForCopilot(queryCaptor.capture());
        assertThat(requestCaptor.getValue().userPrompt()).contains("admissionTicketId: 55");
        assertThat(requestCaptor.getValue().userPrompt()).contains("qrProvided: true");
        assertThat(requestCaptor.getValue().userPrompt()).doesNotContain("secret-qr-token");
        assertThat(queryCaptor.getValue()).contains("admission ticket context present", "qr context present");
        assertThat(queryCaptor.getValue()).doesNotContain("secret-qr-token", "55");
    }

    @Test
    void includesTrustedPolicyContextInCopilotPrompt() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(10L, PlatformRole.USER);
        given(promptProvider.get(com.min.edu.ai.prompt.PromptType.AI_COPILOT))
            .willReturn("system prompt");
        given(policyRetrievalService.retrieveForCopilot(anyString()))
            .willReturn(new PolicyRetrievalResult(
                true,
                1,
                "Policy Type: REFUND\nSection: EXCHANGE_CODE_ALREADY_REDEEMED\nContent:\npolicy"
            ));
        given(aiModelGateway.chat(any(AiChatRequest.class))).willReturn(new AiChatResult(
            new AiCopilotResponse("Policy answer.", AiCategory.REFUND, false)
        ));

        service.ask(
            100L,
            new AiCopilotRequest("Can I refund a redeemed exchange code?", null),
            actor
        );

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().userPrompt())
            .contains("[INFORMATION_PRIORITY]")
            .contains("JAVA_BACKEND_DECISION")
            .contains("[TRUSTED_POLICY_CONTEXT]")
            .contains("EXCHANGE_CODE_ALREADY_REDEEMED");
    }

    @Test
    void degradesToNoPolicyContextWhenPolicyRetrievalFails() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(10L, PlatformRole.USER);
        given(promptProvider.get(com.min.edu.ai.prompt.PromptType.AI_COPILOT))
            .willReturn("system prompt");
        given(policyRetrievalService.retrieveForCopilot(anyString()))
            .willThrow(new IllegalStateException("vector store down"));
        given(aiModelGateway.chat(any(AiChatRequest.class))).willReturn(new AiChatResult(
            new AiCopilotResponse("Tool answer.", AiCategory.GENERAL, false)
        ));

        service.ask(
            100L,
            new AiCopilotRequest("Why does this not work?", null),
            actor
        );

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().userPrompt()).contains("[TRUSTED_POLICY_CONTEXT]\nNONE");
        assertThat(requestCaptor.getValue().tools()).hasSize(5);
    }

    @Test
    void removesSensitiveValuesFromRetrievalQueryAndPromptQuestion() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(10L, PlatformRole.USER);
        given(promptProvider.get(com.min.edu.ai.prompt.PromptType.AI_COPILOT))
            .willReturn("system prompt");
        given(policyRetrievalService.retrieveForCopilot(anyString()))
            .willReturn(PolicyRetrievalResult.empty());
        given(aiModelGateway.chat(any(AiChatRequest.class))).willReturn(new AiChatResult(
            new AiCopilotResponse("Answer.", AiCategory.GENERAL, false)
        ));
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMCJ9.signature";
        String token = "qrToken=abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890";

        service.ask(
            100L,
            new AiCopilotRequest(
                "email a@eventoday.test phone 010-1234-5678 paymentKey=pay_secret_123 " + jwt + " " + token,
                null,
                new AiCopilotRequest.Context("ORDER-SECRET-1", 77L, 88L, null)
            ),
            actor
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(policyRetrievalService).retrieveForCopilot(queryCaptor.capture());
        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());

        assertThat(queryCaptor.getValue())
            .contains("order context present", "admission ticket context present", "exchange code context present")
            .doesNotContain("a@eventoday.test", "010-1234-5678", "pay_secret_123", jwt,
                "abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890", "ORDER-SECRET-1", "77", "88");
        assertThat(requestCaptor.getValue().userPrompt())
            .doesNotContain("a@eventoday.test", "010-1234-5678", "pay_secret_123", jwt,
                "abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890");
    }

    @Test
    void rejectsBlankQuestionBeforeCallingGateway() {
        assertThatThrownBy(() -> service.ask(
                100L,
                new AiCopilotRequest(" ", null),
                new AuthenticatedMemberDto(10L, PlatformRole.USER)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(promptProvider, aiModelGateway, eventOperationAccessService);
    }
}
