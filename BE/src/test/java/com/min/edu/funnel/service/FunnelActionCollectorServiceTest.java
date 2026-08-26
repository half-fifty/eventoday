package com.min.edu.funnel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.funnel.domain.FunnelActionType;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.dto.FunnelActionRequest;
import com.min.edu.funnel.producer.FunnelActionProducer;

@ExtendWith(MockitoExtension.class)
class FunnelActionCollectorServiceTest {

    @Mock
    private FunnelActionProducer funnelActionProducer;

    @InjectMocks
    private FunnelActionCollectorService funnelActionCollectorService;

    @Test
    void collect_buildsEventDtoWithAnonymousIdAndPublishesIt() {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        FunnelActionRequest request = new FunnelActionRequest(
                "b9d8a554-940f-4d72-b6de-711616158aad",
                42L,
                FunnelActionType.VIEW_EVENT_DETAIL,
                occurredAt,
                Map.of());

        funnelActionCollectorService.collect(request, "anon-1", null);

        ArgumentCaptor<FunnelActionEventDto> captor = ArgumentCaptor.forClass(FunnelActionEventDto.class);
        verify(funnelActionProducer).send(captor.capture());

        FunnelActionEventDto published = captor.getValue();
        assertThat(published.sessionId()).isEqualTo(request.sessionId());
        assertThat(published.eventId()).isEqualTo(request.eventId());
        assertThat(published.anonymousId()).isEqualTo("anon-1");
        assertThat(published.userId()).isNull();
        assertThat(published.actionType()).isEqualTo("VIEW_EVENT_DETAIL");
        assertThat(published.occurredAt()).isEqualTo(occurredAt);
        assertThat(published.actionId()).isNotNull();
        assertThat(published.receivedAt()).isNotNull();
    }

    @Test
    void collect_loggedInUser_includesUserId() {
        FunnelActionRequest request = new FunnelActionRequest(
                "b9d8a554-940f-4d72-b6de-711616158aad",
                42L,
                FunnelActionType.OPEN_PURCHASE_MODAL,
                OffsetDateTime.now(),
                Map.of());

        funnelActionCollectorService.collect(request, "anon-1", 7L);

        ArgumentCaptor<FunnelActionEventDto> captor = ArgumentCaptor.forClass(FunnelActionEventDto.class);
        verify(funnelActionProducer).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
    }
}
