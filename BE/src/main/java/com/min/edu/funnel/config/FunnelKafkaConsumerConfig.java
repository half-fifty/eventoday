package com.min.edu.funnel.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.min.edu.funnel.dto.FunnelActionEventDto;

/**
 * notification 도메인의 기본(auto-configured) Kafka consumer factory는
 * spring.kafka.consumer.properties[spring.json.value.default.type]을 NotificationEventDto로 고정하고 있어
 * 그대로 재사용할 수 없다. 그래서 funnel-actions 토픽 전용 ConsumerFactory를 별도로 둔다.
 */
@Configuration
public class FunnelKafkaConsumerConfig {

    public static final String CONTAINER_FACTORY = "funnelActionKafkaListenerContainerFactory";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, FunnelActionEventDto> funnelActionConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "funnel-analytics-service");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        properties.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, FunnelActionEventDto.class.getName());
        properties.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.min.edu.funnel.dto");
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean(CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, FunnelActionEventDto> funnelActionKafkaListenerContainerFactory(
            ConsumerFactory<String, FunnelActionEventDto> funnelActionConsumerFactory,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
        ConcurrentKafkaListenerContainerFactory<String, FunnelActionEventDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(funnelActionConsumerFactory);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
