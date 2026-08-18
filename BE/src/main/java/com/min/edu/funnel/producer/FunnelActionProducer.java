package com.min.edu.funnel.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.min.edu.funnel.config.FunnelKafkaTopicConfig;
import com.min.edu.funnel.dto.FunnelActionEventDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FunnelActionProducer {

    private final KafkaTemplate<String, FunnelActionEventDto> kafkaTemplate;

    /**
     * 분석용 이벤트 발행 실패가 사용자 흐름에 영향을 주면 안 되므로, 결과를 기다리지 않고
     * 실패 시 로그만 남긴다 (notification 도메인의 동기 발행과 의도적으로 다른 정책).
     */
    public void send(FunnelActionEventDto eventDto) {
        kafkaTemplate.send(
                FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC,
                eventDto.sessionId(),
                eventDto)
            .whenComplete((result, exception) -> {
                if (exception != null) {
                    log.warn("퍼널 이벤트 발행 실패: actionId={}", eventDto.actionId(), exception);
                }
            });
    }
}
