package com.min.edu.payment.outbox.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentOutboxClaimService {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final PaymentOutboxProperties properties;

    @Transactional
    public Optional<PaymentOutboxEvent> claim(Long eventId, String leaseOwner) {
        OffsetDateTime now = OffsetDateTime.now();
        int claimed = paymentOutboxEventRepository.claim(
            eventId,
            leaseOwner,
            now,
            now.plus(properties.getLeaseDuration())
        );
        if (claimed == 0) {
            return Optional.empty();
        }
        return paymentOutboxEventRepository.findById(eventId);
    }
}
