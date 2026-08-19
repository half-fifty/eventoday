package com.min.edu.ai.tool;

import com.min.edu.admission.service.ExchangeCodeOperationQueryService;
import com.min.edu.ai.dto.ExchangeCodeAiContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeCodeAiTool implements AiTool<ExchangeCodeAiTool.Input, ExchangeCodeAiContext> {

    private static final String TOOL_NAME = "getExchangeCodeStatus";

    private final ExchangeCodeOperationQueryService exchangeCodeOperationQueryService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ExchangeCodeAiContext execute(Input input, AiToolContext context) {
        if (input == null || input.exchangeCodeId() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return ExchangeCodeAiContext.from(exchangeCodeOperationQueryService.getExchangeCodeStatus(
            context.eventId(),
            context.memberId(),
            input.exchangeCodeId()
        ));
    }

    public record Input(Long exchangeCodeId) {
    }
}
