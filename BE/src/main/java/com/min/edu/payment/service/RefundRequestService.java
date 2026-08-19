package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.policy.RefundEligibilityPolicy;
import com.min.edu.payment.policy.RefundEligibilityPolicy.RefundEligibilityInput;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.TossBankCodes;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundRequestService {

    private static final String ALREADY_CANCELED_PAYMENT = "ALREADY_CANCELED_PAYMENT";
    private static final String TOSS_CANCELED_STATUS = "CANCELED";

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final TossPaymentClient tossPaymentClient;
    private final RefundAttemptRecorder refundAttemptRecorder;
    private final RefundFinalizer refundFinalizer;
    private final RefundFinalizationExceptionTranslator exceptionTranslator;
    private final RefundEligibilityPolicy refundEligibilityPolicy;

    public CreateRefundResponse refund(
            Long memberId,
            String orderAccessToken,
            Long paymentId,
            CreateRefundRequest request) {
        RefundPaymentProjection payment = paymentRepository
            .findRefundPaymentById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));

        validateAccess(payment, memberId, orderAccessToken);

        PaymentRefund existingRefund = paymentRefundRepository
            .findByPaymentId(paymentId)
            .orElse(null);
        if (existingRefund != null) {
            if (existingRefund.isCompleted()) {
                return CreateRefundResponse.of(existingRefund, payment.getOrderNo());
            }

            if (!existingRefund.isFailed()) {
                throw new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
            }
        }

        OffsetDateTime refundAttemptedAt = OffsetDateTime.now();
        validateRefundableBeforeToss(payment, request, refundAttemptedAt);

        PaymentRefund preparedRefund =
            prepareRefundAttempt(payment, memberId, request, refundAttemptedAt);
        if (preparedRefund.isCompleted()) {
            return CreateRefundResponse.of(preparedRefund, payment.getOrderNo());
        }

        TossCancelResponse tossResponse;
        try {
            tossResponse = cancelWithToss(preparedRefund.getId(), payment, request);
            validateTossCancelResponse(payment, tossResponse);
        } catch (RuntimeException exception) {
            if (shouldMarkRefundFailed(exception)) {
                refundAttemptRecorder.markFailed(preparedRefund.getId());
            } else {
                logUncertainTossCancelResult(preparedRefund.getId(), payment, exception);
            }
            throw exception;
        }

        try {
            return refundFinalizer.finalizeRefund(
                preparedRefund.getId(),
                paymentId,
                memberId,
                request,
                tossResponse
            );
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                logLocalFinalizationFailure(
                    preparedRefund.getId(),
                    payment,
                    businessException
                );
                throw businessException;
            }

            logLocalFinalizationFailure(preparedRefund.getId(), payment, exception);
            throw exception;
        }
    }

    private PaymentRefund prepareRefundAttempt(
            RefundPaymentProjection payment,
            Long memberId,
            CreateRefundRequest request,
            OffsetDateTime refundAttemptedAt) {
        try {
            return refundAttemptRecorder.prepare(payment, memberId, request, refundAttemptedAt);
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException == null) {
                throw exception;
            }

            PaymentRefund existingRefund = paymentRefundRepository
                .findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> businessException);
            if (existingRefund.isCompleted()) {
                return existingRefund;
            }

            if (existingRefund.isFailed()) {
                return refundAttemptRecorder.prepare(payment, memberId, request, refundAttemptedAt);
            }

            throw new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
        }
    }

    private void validateRefundableBeforeToss(
            RefundPaymentProjection payment,
            CreateRefundRequest request,
            OffsetDateTime refundAttemptedAt) {
        boolean exchangeCodeRedeemed = exchangeCodeRepository.existsByTicketOrderIdAndStatus(
                payment.getTicketOrderId(),
                ExchangeCodeStatus.REDEEMED);
        refundEligibilityPolicy.requireRefundable(refundEligibilityPolicy.evaluate(
            new RefundEligibilityInput(
                payment.getPaymentStatus(),
                payment.getPaymentOrderStatus(),
                payment.getTicketOrderStatus(),
                payment.getPaymentAmount(),
                payment.getEventEndAt(),
                exchangeCodeRedeemed,
                refundAttemptedAt
            )
        ));

        if (PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod(payment.getPaymentMethod())
                && request.getRefundReceiveAccount() == null) {
            throw new BusinessException(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_REQUIRED);
        }

        if (PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod(payment.getPaymentMethod())
                && !TossBankCodes.isSupportedBankCode(request.getRefundReceiveAccount().getBank())) {
            throw new BusinessException(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_INVALID);
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
            Long refundId,
            RefundPaymentProjection payment,
            CreateRefundRequest request) {
        try {
            return tossPaymentClient.cancel(new TossCancelRequest(
                payment.getPaymentKey(),
                request.getReason(),
                toTossAmount(payment.getPaymentAmount()),
                refundReceiveAccount(payment, request)
            ));
        } catch (TossPaymentClientException exception) {
            if (ALREADY_CANCELED_PAYMENT.equals(exception.getTossErrorCode())) {
                return recoverAlreadyCanceledPayment(payment);
            }

            logTossCancelRejected(refundId, payment, exception);
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private TossCancelRequest.RefundReceiveAccount refundReceiveAccount(
            RefundPaymentProjection payment,
            CreateRefundRequest request) {
        if (!PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod(payment.getPaymentMethod())) {
            return null;
        }

        CreateRefundRequest.RefundReceiveAccountRequest account =
            request.getRefundReceiveAccount();
        if (account == null) {
            throw new BusinessException(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_REQUIRED);
        }
        if (!TossBankCodes.isSupportedBankCode(account.getBank())) {
            throw new BusinessException(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_INVALID);
        }

        return new TossCancelRequest.RefundReceiveAccount(
            account.getBank(),
            account.getAccountNumber(),
            account.getHolderName()
        );
    }

    private TossCancelResponse recoverAlreadyCanceledPayment(RefundPaymentProjection payment) {
        try {
            TossCancelResponse tossPayment =
                tossPaymentClient.getPaymentForRefund(payment.getPaymentKey());
            validateTossCancelResponse(payment, tossPayment);
            return tossPayment;
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
                || response.totalAmount().compareTo(payment.getPaymentAmount()) != 0
                || !TOSS_CANCELED_STATUS.equals(response.status())
                || response.cancels() == null
                || response.cancels().isEmpty()
                || canceledAmount(response).compareTo(payment.getPaymentAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private BigDecimal canceledAmount(TossCancelResponse response) {
        return response.cancels().stream()
            .map(TossCancelResponse.Cancel::cancelAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void logLocalFinalizationFailure(
            Long refundId,
            RefundPaymentProjection payment,
            RuntimeException exception) {
        log.error(
            "Toss refund succeeded but local finalization failed. refundId={}, paymentId={}, paymentOrderId={}, orderNo={}",
            refundId,
            payment.getPaymentId(),
            payment.getPaymentOrderId(),
            payment.getOrderNo(),
            exception
        );
    }

    private void logTossCancelRejected(
            Long refundId,
            RefundPaymentProjection payment,
            TossPaymentClientException exception) {
        log.warn(
            "Toss refund cancel rejected. stage=TOSS_CANCEL, refundId={}, paymentId={}, tossErrorCode={}, exceptionType={}",
            refundId,
            payment.getPaymentId(),
            exception.getTossErrorCode(),
            exception.getClass().getSimpleName()
        );
    }

    private boolean shouldMarkRefundFailed(RuntimeException exception) {
        if (!(exception instanceof BusinessException businessException)) {
            return false;
        }

        GlobalErrorCode errorCode = businessException.getErrorCode();
        return errorCode == GlobalErrorCode.REFUND_REJECTED
            || errorCode == GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID;
    }

    private void logUncertainTossCancelResult(
            Long refundId,
            RefundPaymentProjection payment,
            RuntimeException exception) {
        log.error(
            "Toss refund result is uncertain. refundId={}, paymentId={}, paymentOrderId={}, orderNo={}",
            refundId,
            payment.getPaymentId(),
            payment.getPaymentOrderId(),
            payment.getOrderNo(),
            exception
        );
    }

    private long toTossAmount(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
