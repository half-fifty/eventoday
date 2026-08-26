package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.TicketOrderReliabilityProperties;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketOrderIdempotencyClaimService {

    private final TicketOrderIdempotencyRequestRepository repository;
    private final TicketOrderReliabilityProperties properties;

    @Transactional
    public TicketOrderIdempotencyClaimResult claim(
            String idempotencyKey,
            String requestHash,
            Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(properties.getIdempotencyProcessingTtl());

        int inserted = repository.insertProcessingIfAbsent(
            idempotencyKey,
            requestHash,
            eventId,
            now,
            expiresAt
        );
        if (inserted == 1) {
            TicketOrderIdempotencyRequest request = repository
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));
            return TicketOrderIdempotencyClaimResult.claimed(request);
        }
        return claimExisting(idempotencyKey, requestHash, now, expiresAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey) {
        repository.findByIdempotencyKeyForUpdate(idempotencyKey)
            .ifPresent(request -> request.fail(OffsetDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey, String requestHash, Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        int inserted = repository.insertFailedIfAbsent(idempotencyKey, requestHash, eventId, now);
        if (inserted == 1) {
            return;
        }

        repository.findByIdempotencyKeyForUpdate(idempotencyKey)
            .ifPresent(request -> request.fail(now));
    }

    private TicketOrderIdempotencyClaimResult claimExisting(
            String idempotencyKey,
            String requestHash,
            OffsetDateTime now,
            OffsetDateTime expiresAt) {
        TicketOrderIdempotencyRequest existing = repository
            .findByIdempotencyKeyForUpdate(idempotencyKey)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

        if (existing.hasDifferentRequestHash(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        if (existing.isCompleted()) {
            return TicketOrderIdempotencyClaimResult.completed(existing);
        }

        if (existing.isFailed() || existing.isProcessingExpired(now)) {
            existing.restartProcessing(now, expiresAt);
            return TicketOrderIdempotencyClaimResult.claimed(existing);
        }

        return TicketOrderIdempotencyClaimResult.processing(existing);
    }
}
