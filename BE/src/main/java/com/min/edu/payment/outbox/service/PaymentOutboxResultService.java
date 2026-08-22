package com.min.edu.payment.outbox.service;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentOutboxResultService {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final PaymentOutboxProperties properties;

    @Transactional
    public boolean markPublished(Long eventId, String leaseOwner) {
        return paymentOutboxEventRepository
            .findByIdAndStatusAndLeaseOwner(eventId, PaymentOutboxEventStatus.PROCESSING, leaseOwner)
            .map(event -> {
                event.markPublished(OffsetDateTime.now());
                return true;
            })
            .orElse(false);
    }

    @Transactional
    public boolean markSendFailure(Long eventId, String leaseOwner, Exception exception) {
        return paymentOutboxEventRepository
            .findByIdAndStatusAndLeaseOwner(eventId, PaymentOutboxEventStatus.PROCESSING, leaseOwner)
            .map(event -> {
                String lastError = sanitize(exception);
                if (event.getRetryCount() >= properties.getMaxRetries()) {
                    event.markFailed(lastError);
                    return true;
                }
                event.markRetry(OffsetDateTime.now().plus(backoff(event.getRetryCount() + 1)), lastError);
                return true;
            })
            .orElse(false);
    }

    private Duration backoff(int nextRetryCount) {
        long multiplier = 1L << Math.max(0, nextRetryCount - 1);
        Duration backoff = properties.getInitialBackoff().multipliedBy(multiplier);
        return backoff.compareTo(properties.getMaxBackoff()) > 0
            ? properties.getMaxBackoff()
            : backoff;
    }

    private String sanitize(Exception exception) {
        if (exception == null) {
            return null;
        }
        return exception.getClass().getName();
    }
}
