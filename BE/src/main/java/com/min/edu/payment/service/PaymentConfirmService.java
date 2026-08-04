package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderType;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private static final String TOSS_DONE_STATUS = "DONE";

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentFinalizer paymentFinalizer;
    private final PaymentFinalizationExceptionTranslator exceptionTranslator;

    public ConfirmPaymentResponse confirm(
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        PaymentOrder paymentOrder = paymentOrderRepository
            .findByOrderNo(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        validateBeforeToss(paymentOrder, memberId, orderAccessToken, request);

        if (paymentOrder.isPaid()) {
            return finalizeWithoutToss(request);
        }

        TossConfirmResponse tossResponse = confirmWithToss(request);
        validateTossResponse(request, paymentOrder, tossResponse);

        return finalizeWithLock(request, tossResponse);
    }

    private void validateBeforeToss(
            PaymentOrder paymentOrder,
            Long memberId,
            String orderAccessToken,
            ConfirmPaymentRequest request) {
        if (paymentOrder.getOrderType() != PaymentOrderType.EVENT_TICKET) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        validateAccess(paymentOrder, memberId, orderAccessToken, request.getOrderId());

        if (request.getAmount().compareTo(paymentOrder.getTotalAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (paymentOrder.getTotalAmount().signum() <= 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_NOT_REQUIRED);
        }

        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(paymentOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot(
                request.getPaymentKey(),
                paymentOrder.getId())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
        }

        if (paymentOrder.isPaid()) {
            return;
        }

        if (paymentOrder.getExpiresAt() == null
                || !paymentOrder.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (!paymentOrder.isPaid()
                && (!paymentOrder.isPending() || !ticketOrder.isPendingPayment())) {
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

    private TossConfirmResponse confirmWithToss(ConfirmPaymentRequest request) {
        try {
            return tossPaymentClient.confirm(new TossConfirmRequest(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount()
            ));
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private void validateTossResponse(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder,
            TossConfirmResponse response) {
        if (!request.getPaymentKey().equals(response.paymentKey())
                || !request.getOrderId().equals(response.orderId())
                || response.totalAmount() == null
                || response.totalAmount().compareTo(paymentOrder.getTotalAmount()) != 0
                || !TOSS_DONE_STATUS.equals(response.status())
                || response.approvedAt() == null
                || response.requestedAt() == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private ConfirmPaymentResponse finalizeWithoutToss(ConfirmPaymentRequest request) {
        return finalizeWithLock(
            request,
            new TossConfirmResponse(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount(),
                TOSS_DONE_STATUS,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
            )
        );
    }

    private ConfirmPaymentResponse finalizeWithLock(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        try {
            return paymentFinalizer.finalizePayment(request, tossResponse);
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                throw businessException;
            }

            throw exception;
        }
    }
}
