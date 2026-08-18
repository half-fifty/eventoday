package com.min.edu.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiCategory;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;
import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.ai.tool.AiToolContext;
import com.min.edu.ai.tool.TicketOrderAiTool;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AiCopilotServiceTest {

    private final PromptProvider promptProvider = Mockito.mock(PromptProvider.class);
    private final TicketOrderAiTool ticketOrderAiTool = Mockito.mock(TicketOrderAiTool.class);
    private final AiModelGateway aiModelGateway = Mockito.mock(AiModelGateway.class);
    private final AiCopilotService service = new AiCopilotService(
        promptProvider,
        ticketOrderAiTool,
        aiModelGateway
    );

    @Test
    void buildsPromptWithToolContextAndReturnsGatewayResult() {
        AiToolContext context = new AiToolContext(10L, PlatformRole.USER, "guest-token", "req-1");
        given(promptProvider.get(PromptType.AI_COPILOT)).willReturn("system prompt");
        given(ticketOrderAiTool.execute("ORDER-1", context)).willReturn(ticketContext());
        AiCopilotResponse expected = new AiCopilotResponse(
            "The order is still waiting for payment, so tickets are not confirmed.",
            AiCategory.TICKET_ORDER,
            false
        );
        given(aiModelGateway.chat(any(AiChatRequest.class)))
            .willReturn(new AiChatResult(expected));

        AiCopilotResponse response = service.ask(
            new AiCopilotRequest("Why have tickets not been issued for this order?", "ORDER-1"),
            context
        );

        assertThat(response).isEqualTo(expected);
        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiModelGateway).chat(requestCaptor.capture());
        AiChatRequest gatewayRequest = requestCaptor.getValue();
        assertThat(gatewayRequest.systemPrompt()).isEqualTo("system prompt");
        assertThat(gatewayRequest.userPrompt()).contains("ORDER-1");
        assertThat(gatewayRequest.userPrompt()).contains("PENDING_PAYMENT");
        assertThat(gatewayRequest.userPrompt()).doesNotContain("guest-token");
    }

    @Test
    void skipsToolWhenOrderNoIsMissing() {
        given(promptProvider.get(PromptType.AI_COPILOT)).willReturn("system prompt");
        AiCopilotResponse expected = new AiCopilotResponse(
            "An order number is required to check that status.",
            AiCategory.GENERAL,
            true
        );
        given(aiModelGateway.chat(any(AiChatRequest.class)))
            .willReturn(new AiChatResult(expected));

        AiCopilotResponse response = service.ask(
            new AiCopilotRequest("Tell me the order status.", null),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1")
        );

        assertThat(response.needsHumanSupport()).isTrue();
        verifyNoInteractions(ticketOrderAiTool);
    }

    @Test
    void rejectsBlankQuestionBeforeCallingGateway() {
        assertThatThrownBy(() -> service.ask(
                new AiCopilotRequest(" ", "ORDER-1"),
                new AiToolContext(10L, PlatformRole.USER, null, "req-1")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(promptProvider, ticketOrderAiTool, aiModelGateway);
    }

    @Test
    void rejectsTooLongQuestionBeforeCallingGateway() {
        String question = "a".repeat(2001);

        assertThatThrownBy(() -> service.ask(
                new AiCopilotRequest(question, "ORDER-1"),
                new AiToolContext(10L, PlatformRole.USER, null, "req-1")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(promptProvider, ticketOrderAiTool, aiModelGateway);
    }

    private TicketOrderAiContext ticketContext() {
        return new TicketOrderAiContext(
            "ORDER-1",
            "Eventoday Conference",
            2,
            BigDecimal.valueOf(20000),
            "PENDING",
            "PENDING_PAYMENT",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00")
        );
    }
}
