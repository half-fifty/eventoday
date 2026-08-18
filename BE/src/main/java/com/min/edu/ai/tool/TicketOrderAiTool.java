package com.min.edu.ai.tool;

import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.service.TicketOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketOrderAiTool implements AiTool<String, TicketOrderAiContext> {

    private static final String TOOL_NAME = "getTicketOrderStatus";

    private final TicketOrderQueryService ticketOrderQueryService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public TicketOrderAiContext execute(String orderNo, AiToolContext context) {
        TicketOrderDetailResponse response = ticketOrderQueryService.getTicketOrderDetail(
            orderNo,
            context.memberId(),
            context.guestOrderAccessToken()
        );
        return TicketOrderAiContext.from(response);
    }
}
