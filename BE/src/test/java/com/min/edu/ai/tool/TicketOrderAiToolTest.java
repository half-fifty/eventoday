package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.service.TicketOrderOperationQueryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TicketOrderAiToolTest {

    private final TicketOrderOperationQueryService ticketOrderOperationQueryService =
        Mockito.mock(TicketOrderOperationQueryService.class);
    private final TicketOrderAiTool tool = new TicketOrderAiTool(ticketOrderOperationQueryService);

    @Test
    void delegatesToOperationQueryServiceAndReturnsSafeDto() {
        AiToolContext context = new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L);
        given(ticketOrderOperationQueryService.getTicketOrderStatus(100L, 10L, "ORDER-1"))
            .willReturn(view());

        TicketOrderAiContext result = tool.execute(new TicketOrderAiTool.Input("ORDER-1"), context);

        verify(ticketOrderOperationQueryService).getTicketOrderStatus(100L, 10L, "ORDER-1");
        assertThat(result.orderNo()).isEqualTo("ORDER-1");
        assertThat(result.eventName()).isEqualTo("Eventoday Conference");
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.totalAmount()).isEqualByComparingTo("20000");
        assertThat(result.paymentOrderStatus()).isEqualTo("PAID");
        assertThat(result.ticketOrderStatus()).isEqualTo("CONFIRMED");
    }

    private TicketOrderOperationQueryService.TicketOrderOperationView view() {
        return new TicketOrderOperationQueryService.TicketOrderOperationView(
            "ORDER-1",
            "Eventoday Conference",
            2,
            BigDecimal.valueOf(20000),
            "PAID",
            "CONFIRMED",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00"),
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00")
        );
    }
}
