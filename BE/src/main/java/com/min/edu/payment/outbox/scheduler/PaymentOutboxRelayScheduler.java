package com.min.edu.payment.outbox.scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;
import com.min.edu.payment.outbox.service.PaymentOutboxPublishRunner;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "payment.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PaymentOutboxRelayScheduler {

    private final String workerId = UUID.randomUUID().toString();

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final PaymentOutboxPublishRunner publishRunner;
    private final PaymentOutboxProperties properties;

    @Scheduled(fixedDelayString = "#{@paymentOutboxProperties.fixedDelay.toMillis()}")
    public void relay() {
        List<Long> candidateIds = paymentOutboxEventRepository.findClaimableIds(
            OffsetDateTime.now(),
            PageRequest.of(0, properties.getBatchSize())
        );

        for (Long candidateId : candidateIds) {
            publishRunner.publish(candidateId, workerId);
        }
    }
}
