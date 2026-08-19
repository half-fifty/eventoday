package com.min.edu.funnel.producer;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.min.edu.funnel.config.FunnelKafkaTopicConfig;
import com.min.edu.funnel.dto.FunnelActionEventDto;

@ExtendWith(MockitoExtension.class)
class FunnelActionProducerTest {

    @Mock
    private KafkaTemplate<String, FunnelActionEventDto> kafkaTemplate;

    @InjectMocks
    private FunnelActionProducer funnelActionProducer;

    private FunnelActionEventDto eventDto() {
        return FunnelActionEventDto.create(
                "b9d8a554-940f-4d72-b6de-711616158aad",
                42L,
                "anon-1",
                null,
                "VIEW_EVENT_DETAIL",
                OffsetDateTime.now(),
                Map.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_publishesEventWithSessionIdAsKafkaKey() {
        FunnelActionEventDto eventDto = eventDto();
        SendResult<String, FunnelActionEventDto> sendResult = mock(SendResult.class);
        given(kafkaTemplate.send(FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC, eventDto.sessionId(), eventDto))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        funnelActionProducer.send(eventDto);

        verify(kafkaTemplate).send(
                FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC,
                eventDto.sessionId(),
                eventDto);
    }

    @Test
    void send_kafkaSendFails_doesNotThrow() {
        FunnelActionEventDto eventDto = eventDto();
        CompletableFuture<SendResult<String, FunnelActionEventDto>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("브로커 응답 없음"));
        given(kafkaTemplate.send(FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC, eventDto.sessionId(), eventDto))
                .willReturn(failedFuture);

        // 결제 등 사용자 흐름과 무관한 분석 이벤트이므로, 발행 실패가 호출자에게 예외로 전파되면 안 된다.
        funnelActionProducer.send(eventDto);
    }
}
