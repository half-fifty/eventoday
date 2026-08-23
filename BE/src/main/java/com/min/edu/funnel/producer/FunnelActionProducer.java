package com.min.edu.funnel.producer;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * FE가 직접 트리거하는 수집 경로 전용 — 재시도 보장이 필요하면 {@link #sendAndWait} 사용.
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

    /**
     * 결제완료 등 outbox를 통해 발행하는 경로 전용. outbox는 "발행 성공"을 기준으로 재시도 여부를
     * 판단하므로, 브로커 ack을 실제로 기다렸다가 실패/타임아웃이면 예외를 던져 호출자가 재시도로
     * 이어지게 한다 (fire-and-forget인 {@link #send}와 의도적으로 다른 정책).
     */
    public void sendAndWait(FunnelActionEventDto eventDto, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(
                FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC,
                eventDto.sessionId(),
                eventDto)
            .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
