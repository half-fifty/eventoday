package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.service.AdmissionTicketOperationQueryService;
import com.min.edu.ai.dto.AdmissionTicketAiContext;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionTicketAiToolTest {

    private final AdmissionTicketOperationQueryService admissionTicketOperationQueryService =
        Mockito.mock(AdmissionTicketOperationQueryService.class);
    private final AdmissionTicketAiTool tool =
        new AdmissionTicketAiTool(admissionTicketOperationQueryService);

    @Test
    void delegatesToOperationQueryServiceAndReturnsSafeStatus() {
        given(admissionTicketOperationQueryService.getAdmissionTicketStatus(100L, 10L, 11L))
            .willReturn(view());

        AdmissionTicketAiContext result = tool.execute(
            new AdmissionTicketAiTool.Input(11L),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L)
        );

        verify(admissionTicketOperationQueryService).getAdmissionTicketStatus(100L, 10L, 11L);
        assertThat(result.admissionTicketId()).isEqualTo(11L);
        assertThat(result.eventId()).isEqualTo(100L);
        assertThat(result.qrAvailable()).isTrue();
        assertThat(result.status()).isEqualTo(AdmissionTicketStatus.ISSUED);
    }

    @Test
    void safeDtoDoesNotDeclareSensitiveFields() {
        assertThat(Arrays.stream(AdmissionTicketAiContext.class.getDeclaredFields())
            .map(field -> field.getName().toLowerCase())
            .toList()).doesNotContain("qrtoken", "email", "phone", "secret");
    }

    private AdmissionTicketOperationQueryService.AdmissionTicketOperationView view() {
        return new AdmissionTicketOperationQueryService.AdmissionTicketOperationView(
            11L,
            100L,
            "Eventoday Conference",
            AdmissionTicketStatus.ISSUED,
            true,
            OffsetDateTime.parse("2026-08-18T09:00:00+09:00"),
            null,
            null,
            ExchangeCodeStatus.REDEEMED
        );
    }
}
