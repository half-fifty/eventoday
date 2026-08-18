package com.min.edu.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.ai.tool.AiToolContext;
import com.min.edu.ai.tool.TicketOrderAiTool;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiCopilotService {

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final PromptProvider promptProvider;
    private final TicketOrderAiTool ticketOrderAiTool;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AiCopilotResponse ask(AiCopilotRequest request, AiToolContext context) {
        validateQuestion(request);

        String systemPrompt = promptProvider.get(PromptType.AI_COPILOT);
        String userPrompt = buildUserPrompt(request, context);
        return aiModelGateway.chat(new AiChatRequest(systemPrompt, userPrompt)).response();
    }

    private void validateQuestion(AiCopilotRequest request) {
        if (request == null
                || !StringUtils.hasText(request.question())
                || request.question().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildUserPrompt(AiCopilotRequest request, AiToolContext context) {
        TicketOrderAiContext ticketOrderContext = null;
        if (StringUtils.hasText(request.orderNo())) {
            ticketOrderContext = ticketOrderAiTool.execute(request.orderNo().trim(), context);
        }

        return """
            User question:
            %s

            Eventoday read-only context available to answer:
            %s

            Respond only with valid JSON matching this schema:
            {
              "answer": "Korean answer shown to the user",
              "category": "PAYMENT|TICKET_ORDER|REFUND|ADMISSION|EVENT|GENERAL",
              "needsHumanSupport": false
            }
            """.formatted(
            request.question().trim(),
            toJson(ticketOrderContext)
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }
}
