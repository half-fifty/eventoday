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
}