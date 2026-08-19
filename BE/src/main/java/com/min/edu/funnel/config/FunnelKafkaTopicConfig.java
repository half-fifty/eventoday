package com.min.edu.funnel.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FunnelKafkaTopicConfig {

    public static final String FUNNEL_ACTIONS_TOPIC = "funnel-actions";

    @Bean
    public NewTopic funnelActionsTopic() {
        return TopicBuilder.name(FUNNEL_ACTIONS_TOPIC)
                .partitions(3) // sessionId를 파티션 키로 써서 세션 단위 순서를 보장하기 위해 분산
                .replicas(1)
                .build();
    }
}
