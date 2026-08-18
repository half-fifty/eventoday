package com.min.edu.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.ai.config.AiProperties;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class SpringAiModelGateway implements AiModelGateway {

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public SpringAiModelGateway(
            @Qualifier("copilotChatModel") ChatModel chatModel,
            AiProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public AiChatResult chat(AiChatRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("AI copilot API key is not configured. provider={}, model={}",
                properties.provider(), properties.model());
            throw new BusinessException(GlobalErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        long startedAt = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(new Prompt(
                new SystemMessage(request.systemPrompt()),
                new UserMessage(request.userPrompt())
            ));
            String content = extractContent(response);
            AiCopilotResponse parsed = parseResponse(content);
            log.info("AI copilot call succeeded. provider={}, model={}, latencyMs={}",
                properties.provider(), properties.model(), elapsedMillis(startedAt));
            return new AiChatResult(parsed);
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

    private AiCopilotResponse parseResponse(String content) {
        try {
            AiCopilotResponse response =
                objectMapper.readValue(stripMarkdownFence(content), AiCopilotResponse.class);
            validateStructuredResponse(response);
            return response;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private void validateStructuredResponse(AiCopilotResponse response) {
        if (response == null
                || !StringUtils.hasText(response.answer())
                || response.category() == null) {
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
