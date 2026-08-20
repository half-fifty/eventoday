package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.TicketOrderReliabilityProperties;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestOrderAccessTokenRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.exception.TicketOrderBusyException;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;
import com.min.edu.payment.repository.TicketOrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    private final TicketOrderRequestHasher requestHasher;
    private final TicketOrderInflightDuplicateGate inflightDuplicateGate;
    private final TicketOrderIdempotencyClaimService idempotencyClaimService;
    private final TicketOrderAdmissionGate admissionGate;
    private final TicketOrderCreationProcessor creationProcessor;
    private final TicketOrderIdempotencyRequestRepository idempotencyRequestRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final GuestOrderAccessService guestOrderAccessService;
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
        boolean idempotencyClaimed = false;
        try {
            AdmissionResult admissionResult = admissionGate.tryAcquire(eventId, idempotencyKey);
            if (admissionResult == AdmissionResult.REJECTED) {
                return handleAdmissionRejected(idempotencyKey, requestHash, request);
            }
            admissionAcquired = admissionResult == AdmissionResult.ACQUIRED;

            TicketOrderIdempotencyClaimResult claim = idempotencyClaimService.claim(
                idempotencyKey,
                requestHash,
                eventId
            );

            if (claim.status() == TicketOrderIdempotencyClaimStatus.COMPLETED) {
                return completedResponse(claim.request(), request);
            }

            if (claim.status() == TicketOrderIdempotencyClaimStatus.PROCESSING) {
                throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            }

            idempotencyClaimed = true;
            return creationProcessor.create(idempotencyKey, eventId, buyerMemberId, request);
        } catch (RuntimeException exception) {
            markFailedIfBusinessAttemptStarted(idempotencyKey, idempotencyClaimed, exception);
            throw exception;
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
            return completedResponse(existing, request);
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
            return completedResponse(existing, request);
        }

        throw ticketOrderBusy();
    }

    private void markFailedIfBusinessAttemptStarted(
            String idempotencyKey,
            boolean idempotencyClaimed,
            RuntimeException exception) {
        if (!idempotencyClaimed) {
            return;
        }

        if (exception instanceof BusinessException businessException
                && (businessException.getErrorCode() == GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT
                || businessException.getErrorCode() == GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS
                || businessException.getErrorCode() == GlobalErrorCode.INVALID_INPUT_VALUE
                || businessException.getErrorCode() == GlobalErrorCode.TICKET_ORDER_BUSY)) {
            return;
        }

        idempotencyClaimService.markFailed(idempotencyKey);
    }

    private TicketOrderBusyException ticketOrderBusy() {
        return new TicketOrderBusyException(
            reliabilityProperties.getAdmission().getRetryAfterSeconds()
        );
    }

    private CreateTicketOrderResponse completedResponse(
            TicketOrderIdempotencyRequest idempotencyRequest,
            CreateTicketOrderRequest retryRequest) {
        PaymentOrder paymentOrder = paymentOrderRepository.findById(idempotencyRequest.getPaymentOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        TicketOrder ticketOrder = ticketOrderRepository.findById(idempotencyRequest.getTicketOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (ticketOrder.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
            List<ExchangeCode> exchangeCodes =
                exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId());
            return CreateTicketOrderResponse.free(
                paymentOrder,
                ticketOrder,
                exchangeCodes,
                issueGuestAccessTokenIfNeeded(paymentOrder, retryRequest)
            );
        }

        return CreateTicketOrderResponse.paymentPending(
            paymentOrder,
            ticketOrder,
            issueGuestAccessTokenIfNeeded(paymentOrder, retryRequest)
        );
    }

    private String issueGuestAccessTokenIfNeeded(
            PaymentOrder paymentOrder,
            CreateTicketOrderRequest retryRequest) {
        if (paymentOrder.getBuyerMemberId() != null) {
            return null;
        }

        if (retryRequest.getBuyer() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_GUEST_BUYER_INFO);
        }

        return guestOrderAccessService.issueGuestAccessToken(
            paymentOrder.getOrderNo(),
            new GuestOrderAccessTokenRequest(
                retryRequest.getBuyer().getEmail(),
                retryRequest.getBuyer().getPhone()
            )
        ).orderAccessToken();
    }
}
