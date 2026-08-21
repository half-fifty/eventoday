package com.min.edu.payment.outbox.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.min.edu.payment.config.PaymentOutboxProperties;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxLeaseHeartbeat {

    private final PaymentOutboxLeaseService leaseService;
    private final PaymentOutboxProperties properties;
    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "payment-outbox-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

    public LeaseHeartbeat start(Long eventId, String leaseOwner) {
        long intervalMillis = properties.getLeaseRenewalInterval().toMillis();
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
            () -> renew(eventId, leaseOwner),
            intervalMillis,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
        return new LeaseHeartbeat(future);
    }

    private void renew(Long eventId, String leaseOwner) {
        try {
            boolean renewed = leaseService.renew(eventId, leaseOwner);
            if (!renewed) {
                log.warn(
                    "Payment outbox lease renewal skipped. id={}, leaseOwner={}",
                    eventId,
                    leaseOwner
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                "Payment outbox lease renewal failed. id={}, leaseOwner={}, errorType={}",
                eventId,
                leaseOwner,
                exception.getClass().getName()
            );
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public static class LeaseHeartbeat implements AutoCloseable {

        private final ScheduledFuture<?> future;

        private LeaseHeartbeat(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}
