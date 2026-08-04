package com.min.edu.payment.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final String TOSS_DONE_STATUS = "DONE";

    private final TossPaymentClient tossPaymentClient;
    private final PaymentFinalizer paymentFinalizer;
    private final PaymentFinalizationExceptionTranslator exceptionTranslator;

    public void handleTossWebhook(TossPaymentWebhookRequest request) {
        if (!PAYMENT_STATUS_CHANGED.equals(request.getEventType())) {
            return;
        }

        TossConfirmResponse tossPayment = getPayment(request.getData().getPaymentKey());
        validateWebhookMatchesToss(request.getData(), tossPayment);

        if (!TOSS_DONE_STATUS.equals(tossPayment.status())) {
            return;
        }

        validateDonePayment(tossPayment);
        finalizePayment(tossPayment);
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
}
