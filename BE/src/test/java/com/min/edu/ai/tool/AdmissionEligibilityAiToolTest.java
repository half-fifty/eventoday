package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.service.AdmissionEligibilityQueryService;
import com.min.edu.ai.dto.AdmissionEligibilityAiContext;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionEligibilityAiToolTest {

    private final AdmissionEligibilityQueryService admissionEligibilityQueryService =
        Mockito.mock(AdmissionEligibilityQueryService.class);
    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final AdmissionEligibilityAiTool tool =
        new AdmissionEligibilityAiTool(admissionEligibilityQueryService, eventOperationAccessService);

    @Test
    void usesSharedQueryServicePolicyAndReturnsReasonCodeWithoutStateChange() {
        OffsetDateTime usedAt = OffsetDateTime.parse("2026-08-18T09:00:00+09:00");
        given(admissionEligibilityQueryService.evaluateByTicketId(100L, 11L))
            .willReturn(new AdmissionEligibilityResult(
                false,
                AdmissionEligibilityReasonCode.ALREADY_USED,
                AdmissionTicketStatus.USED,
                EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-08-18T20:00:00+09:00"),
                usedAt
            ));

        AdmissionEligibilityAiContext result = tool.execute(
            new AdmissionEligibilityAiTool.Input(11L),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L)
        );

        verify(eventOperationAccessService).requireOperationalAccess(100L, 10L);
        verify(admissionEligibilityQueryService).evaluateByTicketId(100L, 11L);
        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(AdmissionEligibilityReasonCode.ALREADY_USED);
    }

    @Test
    void doesNotCallDomainCheckIn() {
        AdmissionTicket ticket = Mockito.mock(AdmissionTicket.class);
        given(admissionEligibilityQueryService.evaluateByTicketId(100L, 11L))
            .willReturn(new AdmissionEligibilityResult(
                true,
                AdmissionEligibilityReasonCode.ELIGIBLE,
                AdmissionTicketStatus.ISSUED,
                EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-08-18T20:00:00+09:00"),
                null
            ));

        tool.execute(new AdmissionEligibilityAiTool.Input(11L),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L));

        verify(ticket, never()).checkIn(Mockito.any());
    }
}
