package com.min.edu.payment.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.support.PaymentSecretHasher;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final String DEPOSIT_CALLBACK = "DEPOSIT_CALLBACK";
    private static final String TOSS_DONE_STATUS = "DONE";
    private static final String TOSS_WAITING_FOR_DEPOSIT_STATUS = "WAITING_FOR_DEPOSIT";

    private final TossPaymentClient tossPaymentClient;
    private final PaymentFinalizer paymentFinalizer;
    private final PaymentFinalizationExceptionTranslator exceptionTranslator;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentVirtualAccountRepository virtualAccountRepository;

    public void handleTossWebhook(TossPaymentWebhookRequest request) {
        if (DEPOSIT_CALLBACK.equals(request.getEventType())
                || isDepositCallbackBody(request)) {
            handleDepositCallback(request);
            return;
        }

        if (!PAYMENT_STATUS_CHANGED.equals(request.getEventType())) {
            return;
        }

        TossConfirmResponse tossPayment = getPayment(request.getData().getPaymentKey());
        validateWebhookMatchesToss(request.getData(), tossPayment);

        if (!TOSS_DONE_STATUS.equals(tossPayment.status())) {
            warnIfStatusReversedAfterPaid(tossPayment);
            return;
        }

        validateDonePayment(tossPayment);
        finalizePayment(tossPayment);
    }

    private void handleDepositCallback(TossPaymentWebhookRequest request) {
        if (request.getOrderId() == null
                || request.getOrderId().isBlank()
                || request.getSecret() == null
                || request.getSecret().isBlank()
                || request.getStatus() == null
                || request.getStatus().isBlank()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }

        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNo(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByPaymentOrderId(paymentOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));
        PaymentVirtualAccount virtualAccount = virtualAccountRepository.findByPaymentId(payment.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (!PaymentSecretHasher.sha256(request.getSecret())
                .equals(virtualAccount.getWebhookSecretHash())) {
            throw new BusinessException(GlobalErrorCode.VIRTUAL_ACCOUNT_SECRET_MISMATCH);
        }

        TossConfirmResponse tossPayment = getPaymentByOrderId(request.getOrderId());
        validateDepositCallbackMatchesToss(request, payment, paymentOrder, tossPayment);

        if (TOSS_DONE_STATUS.equals(tossPayment.status())) {
            validateDonePayment(tossPayment);
            finalizePayment(tossPayment);
            return;
        }

        if (TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(tossPayment.status())) {
            warnIfStatusReversedAfterPaid(tossPayment);
        }
    }

    private TossConfirmResponse getPayment(String paymentKey) {
        try {
            TossConfirmResponse tossPayment = tossPaymentClient.getPayment(paymentKey);
            if (tossPayment == null) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
            }

            return tossPayment;
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private TossConfirmResponse getPaymentByOrderId(String orderId) {
        try {
            TossConfirmResponse tossPayment = tossPaymentClient.getPaymentByOrderId(orderId);
            if (tossPayment == null) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
            }

            return tossPayment;
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private void validateWebhookMatchesToss(
            TossPaymentWebhookRequest.PaymentData webhookPayment,
            TossConfirmResponse tossPayment) {
        if (TOSS_DONE_STATUS.equals(webhookPayment.getStatus())
                && (webhookPayment.getRequestedAt() == null
                    || webhookPayment.getApprovedAt() == null)) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }

        if (!Objects.equals(webhookPayment.getPaymentKey(), tossPayment.paymentKey())
                || !Objects.equals(webhookPayment.getOrderId(), tossPayment.orderId())
                || tossPayment.totalAmount() == null
                || webhookPayment.getTotalAmount().compareTo(tossPayment.totalAmount()) != 0
                || !Objects.equals(webhookPayment.getStatus(), tossPayment.status())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }

        if (webhookPayment.getApprovedAt() != null
                && tossPayment.approvedAt() != null
                && !webhookPayment.getApprovedAt().toInstant()
                    .equals(tossPayment.approvedAt().toInstant())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private void validateDonePayment(TossConfirmResponse tossPayment) {
        if (tossPayment.paymentKey() == null
                || tossPayment.orderId() == null
                || tossPayment.totalAmount() == null
                || !TOSS_DONE_STATUS.equals(tossPayment.status())
                || tossPayment.requestedAt() == null
                || tossPayment.approvedAt() == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private void validateDepositCallbackMatchesToss(
            TossPaymentWebhookRequest webhook,
            Payment payment,
            PaymentOrder paymentOrder,
            TossConfirmResponse tossPayment) {
        if (!Objects.equals(webhook.getOrderId(), tossPayment.orderId())
                || !Objects.equals(payment.getPaymentKey(), tossPayment.paymentKey())
                || !Objects.equals(paymentOrder.getOrderNo(), tossPayment.orderId())
                || tossPayment.totalAmount() == null
                || tossPayment.totalAmount().compareTo(paymentOrder.getTotalAmount()) != 0
                || !Objects.equals(webhook.getStatus(), tossPayment.status())
                || !PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod(tossPayment.method())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private void finalizePayment(TossConfirmResponse tossPayment) {
        ConfirmPaymentRequest finalizationRequest = new ConfirmPaymentRequest(
            tossPayment.paymentKey(),
            tossPayment.orderId(),
            tossPayment.totalAmount()
        );

        try {
            paymentFinalizer.finalizePaymentFromWebhook(finalizationRequest, tossPayment);
        } catch (RuntimeException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                throw businessException;
            }

            throw exception;
        }
    }

    private boolean isDepositCallbackBody(TossPaymentWebhookRequest request) {
        return request.getEventType() == null
            && request.getSecret() != null
            && request.getStatus() != null
            && request.getOrderId() != null;
    }

    private void warnIfStatusReversedAfterPaid(TossConfirmResponse tossPayment) {
        if (!TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(tossPayment.status())) {
            return;
        }

        paymentRepository.findByPaymentKey(tossPayment.paymentKey())
            .filter(Payment::isPaid)
            .ifPresent(payment -> log.warn(
                "Virtual account status reversal detected. paymentId={}, paymentOrderId={}, orderId={}",
                payment.getId(),
                payment.getPaymentOrderId(),
                tossPayment.orderId()
            ));
    }
}
