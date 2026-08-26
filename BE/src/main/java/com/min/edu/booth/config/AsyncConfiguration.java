package com.min.edu.booth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * - @Async 메서드용 전용 실행기 설정
 * - 부스 빈자리 알림: vacancyNotificationExecutor
 * - outbox 이벤트 발행: outboxRelayExecutor
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * 부스 빈자리 알림 전용 비동기 실행기
     * - Core: 2개 스레드
     * - Max: 5개 스레드
     * - Queue: 100개 작업 대기
     */
    @Bean(name = "vacancyNotificationExecutor")
    public Executor vacancyNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("vacancy-notif-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * outbox 이벤트를 카프카로 발행하는 전용 비동기 실행기.
     * 건당 최대 5초(NotificationProducer 타임아웃) 걸릴 수 있는 작업을 병렬로 처리해,
     * 발행이 느려져도 폴링 스케줄러(relay()) 자체가 막히지 않게 한다.
     * - Core/Max: 8개 스레드 (Kafka/DB 커넥션 풀 규모에 맞춘 상한)
     * - Queue: 200개 작업 대기
     */
    @Bean(name = "outboxRelayExecutor")
    public Executor outboxRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("outbox-relay-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}