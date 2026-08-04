package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundRequestService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final TossPaymentClient tossPaymentClient;
    private final RefundFinalizer refundFinalizer;
    private final RefundFinalizationExceptionTranslator exceptionTranslator;

    public CreateRefundResponse refund(
            Long memberId,
            String orderAccessToken,
            Long paymentId,
            CreateRefundRequest request) {
        RefundPaymentProjection payment = paymentRepository
            .findRefundPaymentById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));

        validateBeforeToss(payment, memberId, orderAccessToken);

        PaymentRefund existingRefund = paymentRefundRepository
            .findByPaymentId(paymentId)
            .orElse(null);
        if (existingRefund != null) {
            if (existingRefund.isCompleted()) {
                return CreateRefundResponse.of(existingRefund, payment.getOrderNo());
            }

            throw new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
        }

        TossCancelResponse tossResponse = cancelWithToss(payment, request);
        validateTossCancelResponse(payment, tossResponse);

        try {
            return refundFinalizer.finalizeRefund(
                paymentId,
                memberId,
                request,
                tossResponse
            );
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                throw businessException;
            }

            throw exception;
        }
    }

    private void validateBeforeToss(
            RefundPaymentProjection payment,
            Long memberId,
            String orderAccessToken) {
        validateAccess(payment, memberId, orderAccessToken);

        if (!PaymentStatus.PAID.name().equals(payment.getPaymentStatus())
                || !PaymentOrderStatus.PAID.name().equals(payment.getPaymentOrderStatus())
                || !TicketOrderStatus.CONFIRMED.name().equals(payment.getTicketOrderStatus())
                || payment.getPaymentAmount().signum() <= 0) {
            throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
        }

        if (!payment.getEventStartAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
        }

        if (exchangeCodeRepository.existsByTicketOrderIdAndStatus(
                payment.getTicketOrderId(),
                ExchangeCodeStatus.REDEEMED)) {
            throw new BusinessException(GlobalErrorCode.USED_TICKET_CANNOT_BE_REFUNDED);
        }
    }

    private void validateAccess(
            RefundPaymentProjection payment,
            Long memberId,
            String orderAccessToken) {
        if (payment.getBuyerMemberId() != null) {
            if (memberId == null) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }

            if (!payment.getBuyerMemberId().equals(memberId)) {
                throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
            }

            return;
        }

        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!payment.getOrderNo().equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
        }
    }

    private TossCancelResponse cancelWithToss(
            RefundPaymentProjection payment,
            CreateRefundRequest request) {
        try {
            return tossPaymentClient.cancel(new TossCancelRequest(
                payment.getPaymentKey(),
                request.getReason(),
                toTossAmount(payment.getPaymentAmount())
            ));
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private void validateTossCancelResponse(
            RefundPaymentProjection payment,
            TossCancelResponse response) {
        if (response == null
                || !payment.getPaymentKey().equals(response.paymentKey())
                || !payment.getOrderNo().equals(response.orderId())
                || response.totalAmount() == null
                || response.totalAmount().compareTo(payment.getPaymentAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private long toTossAmount(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
