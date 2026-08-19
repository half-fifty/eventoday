package com.min.edu.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.ai.dto.EventOperationAiContext;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.member.domain.PlatformRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EventOperationInfoAiToolTest {

    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final EventOperationInfoAiTool tool =
        new EventOperationInfoAiTool(eventOperationAccessService);

    @Test
    void ignoresToolInputEventIdAndUsesServerContextEventId() {
        given(eventOperationAccessService.requireOperationalAccess(100L, 10L))
            .willReturn(event());

        EventOperationAiContext result = tool.execute(
            new EventOperationInfoAiTool.Input(),
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L)
        );

        verify(eventOperationAccessService).requireOperationalAccess(100L, 10L);
        assertThat(result.eventId()).isEqualTo(100L);
        assertThat(result.eventName()).isEqualTo("Eventoday Conference");
        assertThat(result.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(result.venueName()).isEqualTo("Main Hall");
    }

    @Test
    void inputSchemaDoesNotContainEventId() {
        assertThat(EventOperationInfoAiTool.Input.class.getDeclaredFields()).isEmpty();
    }

    private Event event() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return Event.builder()
            .id(100L)
            .organizerOrganizationId(500L)
            .name("Eventoday Conference")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("Main Hall")
            .address("Seoul")
            .startAt(now.minusDays(1))
            .endAt(now.plusDays(1))
            .ticketPrice(BigDecimal.TEN)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(2)
            .status(EventStatus.PUBLISHED)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
