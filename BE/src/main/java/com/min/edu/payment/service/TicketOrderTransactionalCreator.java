package com.min.edu.payment.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketOrderTransactionalCreator {

    private final TicketOrderIdempotencyClaimService idempotencyClaimService;
    private final TicketOrderCreationProcessor creationProcessor;
    private final TicketOrderCompletedResponseService completedResponseService;

    @Transactional
    public CreateTicketOrderResponse execute(
            String idempotencyKey,
            String requestHash,
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request) {
        TicketOrderIdempotencyClaimResult claim = idempotencyClaimService.claim(
            idempotencyKey,
            requestHash,
            eventId
        );

        if (claim.status() == TicketOrderIdempotencyClaimStatus.COMPLETED) {
            return completedResponseService.completedResponse(claim.request(), request);
        }

        if (claim.status() == TicketOrderIdempotencyClaimStatus.PROCESSING) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        try {
            return creationProcessor.create(idempotencyKey, eventId, buyerMemberId, request);
        } catch (RuntimeException exception) {
            throw new TicketOrderBusinessCreationFailedException(exception);
        }
    }
}
