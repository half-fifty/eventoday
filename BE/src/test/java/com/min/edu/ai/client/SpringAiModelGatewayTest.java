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
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
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
}
