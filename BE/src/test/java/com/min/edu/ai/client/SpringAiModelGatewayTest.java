package com.min.edu.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.ai.config.AiProperties;
import com.min.edu.ai.dto.AiCategory;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;
import com.min.edu.ai.dto.AiFailureExplanationOutput;
import com.min.edu.ai.tool.AiTool;
import com.min.edu.ai.tool.AiToolContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiModelGatewayTest {

    @Test
    void parsesStructuredJsonResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("""
            {
              "answer": "The order is waiting for payment.",
              "category": "TICKET_ORDER",
              "needsHumanSupport": false
            }
            """));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        AiChatResult result = gateway.chat(new AiChatRequest("system", "user"));

        assertThat(result.response().answer()).isEqualTo("The order is waiting for payment.");
        assertThat(result.response().category()).isEqualTo(AiCategory.TICKET_ORDER);
        assertThat(result.response().needsHumanSupport()).isFalse();
    }

    @Test
    void executesToolCallAndParsesOnlyFinalModelResponse() {
        StubTool tool = new StubTool();
        ToolLoopChatModel chatModel = new ToolLoopChatModel();
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");
        AiToolContext toolContext = new AiToolContext(
            10L,
            PlatformRole.USER,
            null,
            "request-1",
            100L
        );

        AiChatResult result = gateway.chat(new AiChatRequest(
            "system",
            """
                [TRUSTED_POLICY_CONTEXT]
                Policy Type: ADMISSION
                Section: ALREADY_USED
                Content:
                Already used tickets cannot enter again.
                [/TRUSTED_POLICY_CONTEXT]

                user
                """,
            List.of(tool),
            toolContext
        ));

        assertThat(result.response().answer()).isEqualTo("Tool result handled.");
        assertThat(result.response().category()).isEqualTo(AiCategory.TICKET_ORDER);
        assertThat(chatModel.prompts).hasSize(2);
        assertThat(tool.executionCount).isEqualTo(1);
        assertThat(tool.receivedInput.orderNo()).isEqualTo("ORDER-1");
        assertThat(tool.receivedContext).isSameAs(toolContext);

        Prompt secondPrompt = chatModel.prompts.get(1);
        assertThat(secondPrompt.getContents())
            .contains("[TRUSTED_POLICY_CONTEXT]", "ALREADY_USED")
            .doesNotContain("memberId", "platformRole", "request-1");
        assertThat(secondPrompt.getInstructions())
            .anySatisfy(message -> {
                assertThat(message).isInstanceOf(ToolResponseMessage.class);
                ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
                assertThat(toolResponseMessage.getResponses()).hasSize(1);
                assertThat(toolResponseMessage.getResponses().get(0).name())
                    .isEqualTo("getTicketOrderStatus");
                assertThat(toolResponseMessage.getResponses().get(0).responseData())
                    .contains("ORDER-1", "100");
            });
    }

    @Test
    void doesNotRunToolLoopWhenModelReturnsFinalResponseDirectly() {
        ToolLoopChatModel chatModel = new ToolLoopChatModel(false);
        StubTool tool = new StubTool();
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        AiChatResult result = gateway.chat(new AiChatRequest(
            "system",
            "general question",
            List.of(tool),
            new AiToolContext(10L, PlatformRole.USER, null, "request-1", 100L)
        ));

        assertThat(result.response().answer()).isEqualTo("No tool needed.");
        assertThat(chatModel.prompts).hasSize(1);
        assertThat(tool.executionCount).isZero();
    }

    @Test
    void stripsMarkdownFenceBeforeParsingStructuredResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("""
            ```json
            {"answer":"Please check with staff.","category":"GENERAL","needsHumanSupport":true}
            ```
            """));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        AiChatResult result = gateway.chat(new AiChatRequest("system", "user"));

        assertThat(result.response().category()).isEqualTo(AiCategory.GENERAL);
        assertThat(result.response().needsHumanSupport()).isTrue();
    }

    @Test
    void mapsBlankApiKeyToServiceUnavailableWithoutCallingProvider() {
        ChatModel chatModel = mock(ChatModel.class);
        SpringAiModelGateway gateway = gateway(chatModel, "");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_SERVICE_UNAVAILABLE);
        verifyNoInteractions(chatModel);
    }

    @Test
    void mapsTimeoutToAiRequestTimeout() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class)))
            .willThrow(new RuntimeException(new SocketTimeoutException("read timed out")));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_REQUEST_TIMEOUT);
    }

    @Test
    void mapsProviderFailureToServiceUnavailable() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class)))
            .willThrow(new RuntimeException("provider 5xx"));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsInvalidResponseToAiResponseInvalid() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("not-json"));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void mapsEmptyResponseToAiResponseInvalid() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response(" "));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void mapsBlankStructuredAnswerToAiResponseInvalid() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("""
            {"answer":" ","category":"GENERAL","needsHumanSupport":true}
            """));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() -> gateway.chat(new AiChatRequest("system", "user")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void parsesFailureExplanationStructuredJsonResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("""
            {
              "explanation": "The ticket was already used.",
              "recommendedAction": "Ask staff.",
              "needsHumanSupport": false
            }
            """));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        AiFailureExplanationOutput result =
            gateway.chat(new AiChatRequest("system", "user"), AiFailureExplanationOutput.class);

        assertThat(result.explanation()).isEqualTo("The ticket was already used.");
        assertThat(result.recommendedAction()).isEqualTo("Ask staff.");
        assertThat(result.needsHumanSupport()).isFalse();
    }

    @Test
    void mapsBlankFailureExplanationToAiResponseInvalid() {
        ChatModel chatModel = mock(ChatModel.class);
        given(chatModel.call(any(Prompt.class))).willReturn(response("""
            {"explanation":" ","recommendedAction":"Ask staff.","needsHumanSupport":true}
            """));
        SpringAiModelGateway gateway = gateway(chatModel, "copilot-key");

        assertThatThrownBy(() ->
            gateway.chat(new AiChatRequest("system", "user"), AiFailureExplanationOutput.class))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }

    private SpringAiModelGateway gateway(ChatModel chatModel, String apiKey) {
        return new SpringAiModelGateway(
            chatModel,
            new AiProperties(
                "google",
                apiKey,
                "gemini-3.5-flash-lite",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                700
            ),
            new AiToolCallbackFactory()
        );
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ChatResponse toolCallResponse() {
        AssistantMessage assistantMessage = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "getTicketOrderStatus",
                "{\"orderNo\":\"ORDER-1\"}"
            )))
            .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private class ToolLoopChatModel implements ChatModel {
        private final List<Prompt> prompts = new ArrayList<>();
        private final boolean requestToolFirst;

        ToolLoopChatModel() {
            this(true);
        }

        ToolLoopChatModel(boolean requestToolFirst) {
            this.requestToolFirst = requestToolFirst;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (requestToolFirst && prompts.size() == 1) {
                return toolCallResponse();
            }
            if (!requestToolFirst) {
                return response("""
                    {"answer":"No tool needed.","category":"GENERAL","needsHumanSupport":false}
                    """);
            }
            assertThat(prompt.getInstructions())
                .anyMatch(message -> message instanceof ToolResponseMessage);
            return response("""
                {"answer":"Tool result handled.","category":"TICKET_ORDER","needsHumanSupport":false}
                """);
        }
    }

    private static class StubTool implements AiTool<StubInput, StubOutput> {
        private int executionCount;
        private StubInput receivedInput;
        private AiToolContext receivedContext;

        @Override
        public String name() {
            return "getTicketOrderStatus";
        }

        @Override
        public Class<StubInput> inputType() {
            return StubInput.class;
        }

        @Override
        public StubOutput execute(StubInput input, AiToolContext context) {
            executionCount++;
            receivedInput = input;
            receivedContext = context;
            return new StubOutput(input.orderNo(), context.eventId());
        }
    }

    private record StubInput(String orderNo) {
    }

    private record StubOutput(String orderNo, Long eventId) {
    }
}
