package com.min.edu.payment.outbox.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentOutboxLeaseService {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final PaymentOutboxProperties properties;

    @Transactional
    public boolean renew(Long eventId, String leaseOwner) {
        OffsetDateTime newLeaseUntil = OffsetDateTime.now().plus(properties.getLeaseDuration());
        return paymentOutboxEventRepository.renewLease(eventId, leaseOwner, newLeaseUntil) == 1;
    }
}
