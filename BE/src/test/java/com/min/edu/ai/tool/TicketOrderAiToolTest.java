package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.ai.dto.TicketOrderAiContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.service.TicketOrderQueryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TicketOrderAiToolTest {

    private final TicketOrderQueryService ticketOrderQueryService =
        Mockito.mock(TicketOrderQueryService.class);
    private final TicketOrderAiTool tool = new TicketOrderAiTool(ticketOrderQueryService);

    @Test
    void usesExistingQueryServiceAuthorizationContextAndReturnsSafeDto() {
        AiToolContext context = new AiToolContext(10L, PlatformRole.USER, "guest-token", "req-1");
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 10L, "guest-token"))
            .willReturn(ticketOrderResponse());

        TicketOrderAiContext result = tool.execute("ORDER-1", context);

        verify(ticketOrderQueryService).getTicketOrderDetail("ORDER-1", 10L, "guest-token");
        assertThat(result.orderNo()).isEqualTo("ORDER-1");
        assertThat(result.eventName()).isEqualTo("Eventoday Conference");
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.totalAmount()).isEqualByComparingTo("20000");
        assertThat(result.paymentOrderStatus()).isEqualTo("PENDING");
        assertThat(result.ticketOrderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.expiresAt())
            .isEqualTo(OffsetDateTime.parse("2026-08-03T10:10:00+09:00"));
    }

    @Test
    void propagatesExistingAccessError() {
        AiToolContext context = new AiToolContext(20L, PlatformRole.USER, null, "req-1");
        given(ticketOrderQueryService.getTicketOrderDetail("ORDER-1", 20L, null))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED));

        assertThatThrownBy(() -> tool.execute("ORDER-1", context))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    void safeDtoDoesNotDeclareSensitiveFields() {
        List<String> fieldNames = Arrays.stream(TicketOrderAiContext.class.getDeclaredFields())
            .map(field -> field.getName().toLowerCase())
            .toList();

        assertThat(fieldNames).doesNotContain(
            "paymentkey",
            "jwt",
            "refreshtoken",
            "orderaccesstoken",
            "accountnumber",
            "customername",
            "email",
            "phone",
            "qrtoken",
            "webhooksecret"
        );
    }

    private TicketOrderDetailResponse ticketOrderResponse() {
        return new TicketOrderDetailResponse(
            1L,
            5L,
            "ORDER-1",
            100L,
            "Eventoday Conference",
            2,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            true,
            "PENDING",
            "PENDING_PAYMENT",
            "VIRTUAL_ACCOUNT",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00"),
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            null,
            List.of()
        );
    }
}
