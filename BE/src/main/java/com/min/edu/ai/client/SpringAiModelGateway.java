package com.min.edu.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.ai.config.AiProperties;
import com.min.edu.ai.dto.AiFailureExplanationOutput;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class SpringAiModelGateway implements AiModelGateway {

    private static final int MAX_TOOL_CALL_ROUNDS = 5;

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final AiToolCallbackFactory toolCallbackFactory;
    private final ToolCallingManager toolCallingManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public SpringAiModelGateway(
            @Qualifier("copilotChatModel") ChatModel chatModel,
            AiProperties properties,
            AiToolCallbackFactory toolCallbackFactory) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.toolCallbackFactory = toolCallbackFactory;
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    @Override
    public AiChatResult chat(AiChatRequest request) {
        return new AiChatResult(chat(request, AiCopilotResponse.class));
    }

    @Override
    public <T> T chat(AiChatRequest request, Class<T> responseType) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("AI copilot API key is not configured. provider={}, model={}",
                properties.provider(), properties.model());
            throw new BusinessException(GlobalErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        long startedAt = System.nanoTime();
        try {
            ChatResponse response = callWithToolLoop(prompt(request));
            String content = extractContent(response);
            T parsed = parseResponse(content, responseType);
            log.info("AI copilot call succeeded. provider={}, model={}, latencyMs={}",
                properties.provider(), properties.model(), elapsedMillis(startedAt));
            return parsed;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            GlobalErrorCode errorCode = mapError(exception);
            log.warn(
                "AI copilot call failed. provider={}, model={}, errorCode={}, latencyMs={}, exceptionType={}",
                properties.provider(),
                properties.model(),
                errorCode.name(),
                elapsedMillis(startedAt),
                exception.getClass().getSimpleName()
            );
            throw new BusinessException(errorCode, exception);
        }
    }

    private ChatResponse callWithToolLoop(Prompt initialPrompt) {
        Prompt currentPrompt = initialPrompt;
        ChatResponse response = chatModel.call(currentPrompt);
        int toolCallRounds = 0;

        while (response != null && response.hasToolCalls()) {
            if (++toolCallRounds > MAX_TOOL_CALL_ROUNDS) {
                throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
            }

            ToolExecutionResult toolExecutionResult =
                toolCallingManager.executeToolCalls(currentPrompt, response);
            if (toolExecutionResult.returnDirect()) {
                return new ChatResponse(ToolExecutionResult.buildGenerations(toolExecutionResult));
            }

            currentPrompt = promptWithConversationHistory(
                toolExecutionResult.conversationHistory(),
                initialPrompt
            );
            response = chatModel.call(currentPrompt);
        }

        return response;
    }

    private Prompt promptWithConversationHistory(List<Message> conversationHistory, Prompt initialPrompt) {
        return new Prompt(conversationHistory, initialPrompt.getOptions());
    }

    private Prompt prompt(AiChatRequest request) {
        List<ToolCallback> toolCallbacks = toolCallbackFactory.create(request.tools());
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
            .model(properties.model())
            .maxOutputTokens(properties.maxOutputTokens())
            .responseMimeType("application/json")
            .toolCallbacks(toolCallbacks)
            .toolContext(toolCallbackFactory.toolContext(request.toolContext()))
            .build();
        return new Prompt(
            List.of(
                new SystemMessage(request.systemPrompt()),
                new UserMessage(request.userPrompt())
            ),
            options
        );
    }

    private String extractContent(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
        }
        return response.getResult().getOutput().getText().trim();
    }

    private <T> T parseResponse(String content, Class<T> responseType) {
        try {
            T response = objectMapper.readValue(stripMarkdownFence(content), responseType);
            validateStructuredResponse(response);
            return response;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private void validateStructuredResponse(Object response) {
        if (response instanceof AiCopilotResponse copilotResponse) {
            if (!StringUtils.hasText(copilotResponse.answer())
                    || copilotResponse.category() == null) {
                throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
            }
            return;
        }
        if (response instanceof AiFailureExplanationOutput explanationOutput) {
            if (!StringUtils.hasText(explanationOutput.explanation())
                    || !StringUtils.hasText(explanationOutput.recommendedAction())) {
                throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
            }
            return;
        }
        if (response == null) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private String stripMarkdownFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFenceStart = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFenceStart <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFenceStart).trim();
    }

    private GlobalErrorCode mapError(Throwable throwable) {
        if (hasCause(throwable, SocketTimeoutException.class)
                || hasCauseName(throwable, "TimeoutException")) {
            return GlobalErrorCode.AI_REQUEST_TIMEOUT;
        }
        if (throwable instanceof BusinessException businessException) {
            return businessException.getErrorCode();
        }
        return GlobalErrorCode.AI_SERVICE_UNAVAILABLE;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCauseName(Throwable throwable, String simpleName) {
        Throwable current = throwable;
        while (current != null) {
            if (simpleName.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
