package com.min.edu.funnel.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.min.edu.funnel.config.FunnelKafkaConsumerConfig;
import com.min.edu.funnel.config.FunnelKafkaTopicConfig;
import com.min.edu.funnel.domain.FunnelAction;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.repository.FunnelActionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FunnelActionConsumer {

    private final FunnelActionRepository funnelActionRepository;

    /**
     * actionId를 ES 문서 _id로 그대로 사용해 저장하므로, Kafka가 같은 메시지를 다시 전달해도
     * 같은 문서를 덮어쓰기만 할 뿐 중복 색인되지 않는다 (멱등).
     */
    @KafkaListener(
            topics = FunnelKafkaTopicConfig.FUNNEL_ACTIONS_TOPIC,
            containerFactory = FunnelKafkaConsumerConfig.CONTAINER_FACTORY)
    public void consume(FunnelActionEventDto eventDto) {
        funnelActionRepository.save(FunnelAction.from(eventDto));
    }
}
