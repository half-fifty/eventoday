package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.repository.AdvertisementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmService {

    private static final String TOSS_DONE_STATUS = "DONE";
    private static final String TOSS_WAITING_FOR_DEPOSIT_STATUS = "WAITING_FOR_DEPOSIT";
    private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentFinalizer paymentFinalizer;
    private final VirtualAccountPaymentService virtualAccountPaymentService;
    private final PaymentConfirmInflightDuplicateGate inflightDuplicateGate;
    private final PaymentFinalizationExceptionTranslator exceptionTranslator;
    private final AdvertisementRepository advertisementRepository;

    public ConfirmPaymentResponse confirm(
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        PaymentOrder paymentOrder = paymentOrderRepository
            .findByOrderNo(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        validateBeforeToss(paymentOrder, memberId, orderAccessToken, request);

        if (paymentOrder.isPaid()) {
            return finalizeWithoutToss(memberId, request);
        }

        if (paymentOrder.isWaitingForDeposit()
                && requestedMethod(paymentOrder) == PaymentMethod.VIRTUAL_ACCOUNT) {
            return virtualAccountPaymentService.getWaitingForDeposit(
                paymentOrder,
                request.getPaymentKey()
            );
        }

        PaymentConfirmInflightClaim claim = inflightDuplicateGate.tryClaim(request.getOrderId());
        if (claim.result() == InflightClaimResult.ALREADY_IN_FLIGHT) {
            return handleAlreadyInFlight(memberId, orderAccessToken, request);
        }

        try {
            PaymentOrder latestPaymentOrder = reloadAndValidateBeforeToss(
                memberId,
                orderAccessToken,
                request
            );
            if (latestPaymentOrder.isPaid()) {
                return finalizeWithoutToss(memberId, request);
            }
            if (latestPaymentOrder.isWaitingForDeposit()
                    && requestedMethod(latestPaymentOrder) == PaymentMethod.VIRTUAL_ACCOUNT) {
                return virtualAccountPaymentService.getWaitingForDeposit(
                    latestPaymentOrder,
                    request.getPaymentKey()
                );
            }

            TossConfirmResponse tossResponse = confirmWithToss(request, latestPaymentOrder);
            return handleProviderPayment(memberId, request, latestPaymentOrder, tossResponse);
        } finally {
            if (claim.acquired()) {
                inflightDuplicateGate.release(request.getOrderId(), claim.token());
            }
        }
    }

    private PaymentOrder reloadAndValidateBeforeToss(
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        PaymentOrder latestPaymentOrder = paymentOrderRepository
            .findByOrderNo(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        validateBeforeToss(latestPaymentOrder, memberId, orderAccessToken, request);
        return latestPaymentOrder;
    }

    private void validateBeforeToss(
            PaymentOrder paymentOrder,
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        validateAccess(paymentOrder, memberId, orderAccessToken, request.getOrderId());

        if (request.getAmount().compareTo(paymentOrder.getTotalAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (paymentOrder.getTotalAmount().signum() <= 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_NOT_REQUIRED);
        }

        TicketOrder ticketOrder = null;
        if (paymentOrder.getOrderType() == PaymentOrderType.EVENT_TICKET) {
            ticketOrder = ticketOrderRepository.findByPaymentOrderId(paymentOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        } else if (paymentOrder.getOrderType() == PaymentOrderType.EVENT_AD) {
            var advertisement = advertisementRepository.findByPaymentOrderId(paymentOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
            if (advertisement.getStatus() != AdvertisementStatus.PAYMENT_PENDING && !paymentOrder.isPaid()) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
            }
        } else {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot(
                request.getPaymentKey(),
                paymentOrder.getId())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
        }

        if (paymentOrder.isPaid() || paymentOrder.isWaitingForDeposit()) {
            return;
        }

        if (paymentOrder.getExpiresAt() == null
                || !paymentOrder.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (!paymentOrder.isPaid() && (!paymentOrder.isPending()
                || (ticketOrder != null && !ticketOrder.isPendingPayment()))) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }
    }

    private void validateAccess(
            PaymentOrder paymentOrder,
            Long memberId,
            String orderAccessToken,
            String orderId) {
        if (paymentOrder.getBuyerMemberId() != null) {
            if (memberId == null) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }

            if (!paymentOrder.getBuyerMemberId().equals(memberId)) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_ACCESS_DENIED);
            }

            return;
        }

        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!orderId.equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }

    private TossConfirmResponse confirmWithToss(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder) {
        try {
            return tossPaymentClient.confirm(new TossConfirmRequest(
                request.getPaymentKey(),
                request.getOrderId(),
                toTossAmount(request.getAmount())
            ));
        } catch (TossPaymentClientException exception) {
            if (ALREADY_PROCESSED_PAYMENT.equals(exception.getTossErrorCode())) {
                return recoverAlreadyProcessedPayment(request, paymentOrder);
            }

            if (isAmbiguousConfirmFailure(exception)) {
                return recoverAmbiguousConfirm(request, paymentOrder, exception);
            }

            throw new BusinessException(exception.getErrorCode());
        }
    }

    private ConfirmPaymentResponse handleAlreadyInFlight(
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        PaymentOrder latestPaymentOrder = paymentOrderRepository
            .findByOrderNo(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        validateAccess(latestPaymentOrder, memberId, orderAccessToken, request.getOrderId());

        if (latestPaymentOrder.isPaid()) {
            return finalizeWithoutToss(memberId, request);
        }

        if (latestPaymentOrder.isWaitingForDeposit()
                && requestedMethod(latestPaymentOrder) == PaymentMethod.VIRTUAL_ACCOUNT) {
            return virtualAccountPaymentService.getWaitingForDeposit(
                latestPaymentOrder,
                request.getPaymentKey()
            );
        }

        throw new BusinessException(GlobalErrorCode.PAYMENT_CONFIRM_IN_PROGRESS);
    }

    private ConfirmPaymentResponse handleProviderPayment(
            Long memberId,
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder,
            TossConfirmResponse tossResponse) {
        validateTossResponse(request, paymentOrder, tossResponse);

        if (requestedMethod(paymentOrder) == PaymentMethod.VIRTUAL_ACCOUNT) {
            return virtualAccountPaymentService.saveWaitingForDeposit(
                memberId,
                paymentOrder,
                request.getPaymentKey(),
                tossResponse
            );
        }

        return finalizeWithLock(memberId, request, tossResponse);
    }

    private TossConfirmResponse recoverAlreadyProcessedPayment(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder) {
        try {
            TossConfirmResponse tossPayment =
                tossPaymentClient.getPayment(request.getPaymentKey());
            validateTossResponse(request, paymentOrder, tossPayment);
            return tossPayment;
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private TossConfirmResponse recoverAmbiguousConfirm(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder,
            TossPaymentClientException confirmException) {
        try {
            TossConfirmResponse tossPayment = tossPaymentClient.getPayment(request.getPaymentKey());
            validateTossResponse(request, paymentOrder, tossPayment);
            log.warn(
                "Recovered ambiguous Toss confirm outcome by provider lookup: orderId={}, resultStatus={}",
                request.getOrderId(),
                tossPayment.status()
            );
            return tossPayment;
        } catch (TossPaymentClientException lookupException) {
            throw new BusinessException(confirmException.getErrorCode());
        }
    }

    private boolean isAmbiguousConfirmFailure(TossPaymentClientException exception) {
        return exception.getErrorCode() == GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
            || exception.getErrorCode() == GlobalErrorCode.PAYMENT_GATEWAY_ERROR;
    }

    private void validateTossResponse(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder,
            TossConfirmResponse response) {
        if (response == null) {
            throw invalidTossConfirmResponse("RESPONSE_MISSING", request.getOrderId(), null);
        }
        if (!request.getPaymentKey().equals(response.paymentKey())) {
            throw invalidTossConfirmResponse("PAYMENT_KEY_MISMATCH", request.getOrderId(), response);
        }
        if (!request.getOrderId().equals(response.orderId())) {
            throw invalidTossConfirmResponse("ORDER_ID_MISMATCH", request.getOrderId(), response);
        }
        if (response.totalAmount() == null) {
            throw invalidTossConfirmResponse("AMOUNT_MISSING", request.getOrderId(), response);
        }
        if (response.totalAmount().compareTo(paymentOrder.getTotalAmount()) != 0) {
            throw invalidTossConfirmResponse("AMOUNT_MISMATCH", request.getOrderId(), response);
        }
        if (response.requestedAt() == null) {
            throw invalidTossConfirmResponse("REQUESTED_AT_MISSING", request.getOrderId(), response);
        }

        PaymentMethod requestedMethod = requestedMethod(paymentOrder);
        if (!requestedMethod.matchesTossMethod(response.method())) {
            log.warn(
                "Invalid Toss payment method: reason={}, orderId={}, status={}, method={}",
                "METHOD_MISMATCH",
                safeOrderId(request.getOrderId(), response),
                safeStatus(response),
                safeMethod(response)
            );
            throw new BusinessException(GlobalErrorCode.PAYMENT_METHOD_MISMATCH);
        }

        if (requestedMethod == PaymentMethod.VIRTUAL_ACCOUNT) {
            if (!TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(response.status())) {
                throw invalidTossConfirmResponse("STATUS_MISMATCH", request.getOrderId(), response);
            }
            return;
        }

        if (!TOSS_DONE_STATUS.equals(response.status())) {
            throw invalidTossConfirmResponse("STATUS_MISMATCH", request.getOrderId(), response);
        }
        if (response.approvedAt() == null) {
            throw invalidTossConfirmResponse("APPROVED_AT_INVALID", request.getOrderId(), response);
        }
    }

    private BusinessException invalidTossConfirmResponse(
            String reason,
            String requestOrderId,
            TossConfirmResponse response) {
        log.warn(
            "Invalid Toss confirm response: reason={}, orderId={}, status={}, method={}",
            reason,
            safeOrderId(requestOrderId, response),
            safeStatus(response),
            safeMethod(response)
        );
        return new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
    }

    private String safeOrderId(String requestOrderId, TossConfirmResponse response) {
        return response == null || response.orderId() == null
            ? requestOrderId
            : response.orderId();
    }

    private String safeStatus(TossConfirmResponse response) {
        return response == null ? null : response.status();
    }

    private String safeMethod(TossConfirmResponse response) {
        return response == null ? null : response.method();
    }

    private ConfirmPaymentResponse finalizeWithoutToss(
            Long memberId,
            ConfirmPaymentRequest request) {
        return finalizeWithLock(
            memberId,
            request,
            new TossConfirmResponse(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount(),
                TOSS_DONE_STATUS,
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
            )
        );
    }

    private ConfirmPaymentResponse finalizeWithLock(
            Long memberId,
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        try {
            return paymentFinalizer.finalizePayment(memberId, request, tossResponse);
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                throw businessException;
            }

            throw exception;
        }
    }

    private long toTossAmount(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private PaymentMethod requestedMethod(PaymentOrder paymentOrder) {
        return paymentOrder.getRequestedPaymentMethod() == null
            ? PaymentMethod.CARD
            : paymentOrder.getRequestedPaymentMethod();
    }
}
