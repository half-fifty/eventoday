package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.service.ExchangeCodeOperationQueryService;
import com.min.edu.ai.dto.ExchangeCodeAiContext;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExchangeCodeAiToolTest {

    private final ExchangeCodeOperationQueryService exchangeCodeOperationQueryService =
        Mockito.mock(ExchangeCodeOperationQueryService.class);
    private final ExchangeCodeAiTool tool = new ExchangeCodeAiTool(exchangeCodeOperationQueryService);

    @Test
    void delegatesToOperationQueryServiceAndReturnsSafeStatus() {
        given(exchangeCodeOperationQueryService.getExchangeCodeStatus(100L, 10L, 20L))
            .willReturn(view());

        ExchangeCodeAiContext result = tool.execute(
            new ExchangeCodeAiTool.Input(20L),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L)
        );

        verify(exchangeCodeOperationQueryService).getExchangeCodeStatus(100L, 10L, 20L);
        assertThat(result.exchangeCodeId()).isEqualTo(20L);
        assertThat(result.maskedCode()).isEqualTo("ABCD****WXYZ");
        assertThat(result.status()).isEqualTo(ExchangeCodeStatus.ISSUED);
        assertThat(result.admissionTicketIssued()).isTrue();
    }

    @Test
    void safeDtoDoesNotDeclareSensitiveFields() {
        assertThat(Arrays.stream(ExchangeCodeAiContext.class.getDeclaredFields())
            .map(field -> field.getName().toLowerCase())
            .toList()).doesNotContain("code", "email", "phone", "secret");
    }

    private ExchangeCodeOperationQueryService.ExchangeCodeOperationView view() {
        return new ExchangeCodeOperationQueryService.ExchangeCodeOperationView(
            20L,
            "ABCD****WXYZ",
            ExchangeCodeStatus.ISSUED,
            ExchangeCodeOperationQueryService.ExchangeCodeOperationView.Source.TICKET_ORDER,
            OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
            null,
            true
        );
    }
}
