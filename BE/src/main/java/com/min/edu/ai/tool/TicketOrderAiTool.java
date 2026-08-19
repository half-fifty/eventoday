package com.min.edu.ai.tool;

import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.service.TicketOrderOperationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketOrderAiTool implements AiTool<TicketOrderAiTool.Input, TicketOrderAiContext> {

    private static final String TOOL_NAME = "getTicketOrderStatus";

    private final TicketOrderOperationQueryService ticketOrderOperationQueryService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public TicketOrderAiContext execute(Input input, AiToolContext context) {
        if (input == null || input.orderNo() == null || input.orderNo().isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return TicketOrderAiContext.from(ticketOrderOperationQueryService.getTicketOrderStatus(
            context.eventId(),
            context.memberId(),
            input.orderNo()
        ));
    }

    public record Input(String orderNo) {
    }
}
