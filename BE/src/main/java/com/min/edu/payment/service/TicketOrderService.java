package com.min.edu.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.TicketOrderReliabilityProperties;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.exception.TicketOrderBusyException;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    private final TicketOrderRequestHasher requestHasher;
    private final TicketOrderInflightDuplicateGate inflightDuplicateGate;
    private final TicketOrderIdempotencyClaimService idempotencyClaimService;
    private final TicketOrderAdmissionGate admissionGate;
    private final TicketOrderTransactionalCreator transactionalCreator;
    private final TicketOrderCompletedResponseService completedResponseService;
    private final TicketOrderIdempotencyRequestRepository idempotencyRequestRepository;
    private final TicketOrderReliabilityProperties reliabilityProperties;

    public CreateTicketOrderResponse create(
            String idempotencyKey,
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request) {
        validateIdempotencyKey(idempotencyKey);

        String requestHash = requestHasher.hash(eventId, buyerMemberId, request);
        InflightClaimResult inflightClaim = inflightDuplicateGate.tryClaim(idempotencyKey);
        if (inflightClaim == InflightClaimResult.ALREADY_IN_FLIGHT) {
            return handleAlreadyInFlight(idempotencyKey, requestHash, request);
        }

        boolean admissionAcquired = false;
        try {
            AdmissionResult admissionResult = admissionGate.tryAcquire(eventId, idempotencyKey);
            if (admissionResult == AdmissionResult.REJECTED) {
                return handleAdmissionRejected(idempotencyKey, requestHash, request);
            }
            admissionAcquired = admissionResult == AdmissionResult.ACQUIRED;

            return transactionalCreator.execute(
                idempotencyKey,
                requestHash,
                eventId,
                buyerMemberId,
                request
            );
        } catch (TicketOrderBusinessCreationFailedException exception) {
            idempotencyClaimService.markFailed(idempotencyKey, requestHash, eventId);
            throw exception.original();
        } finally {
            if (admissionAcquired) {
                admissionGate.release(eventId, idempotencyKey);
            }
            if (inflightClaim == InflightClaimResult.ACQUIRED) {
                inflightDuplicateGate.release(idempotencyKey);
            }
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private CreateTicketOrderResponse handleAlreadyInFlight(
            String idempotencyKey,
            String requestHash,
            CreateTicketOrderRequest request) {
        TicketOrderIdempotencyRequest existing = idempotencyRequestRepository
            .findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

        if (existing.hasDifferentRequestHash(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        if (existing.isCompleted()) {
            return completedResponseService.completedResponse(existing, request);
        }

        throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    private CreateTicketOrderResponse handleAdmissionRejected(
            String idempotencyKey,
            String requestHash,
            CreateTicketOrderRequest request) {
        TicketOrderIdempotencyRequest existing = idempotencyRequestRepository
            .findByIdempotencyKey(idempotencyKey)
            .orElse(null);

        if (existing == null) {
            throw ticketOrderBusy();
        }

        if (existing.hasDifferentRequestHash(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        if (existing.isCompleted()) {
            return completedResponseService.completedResponse(existing, request);
        }

        throw ticketOrderBusy();
    }

    private TicketOrderBusyException ticketOrderBusy() {
        return new TicketOrderBusyException(
            reliabilityProperties.getAdmission().getRetryAfterSeconds()
        );
    }
}
