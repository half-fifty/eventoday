package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    private final PaymentFinalizationExceptionTranslator exceptionTranslator =
        new PaymentFinalizationExceptionTranslator();

    @Test
    void handleTossWebhook_finalizesDonePayment() {
        PaymentWebhookService service = service();
        TossPaymentWebhookRequest request = webhook("DONE");
        TossConfirmResponse tossPayment = tossPayment("DONE");
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);

        service.handleTossWebhook(request);

        verify(paymentFinalizer).finalizePaymentFromWebhook(
            any(ConfirmPaymentRequest.class),
            org.mockito.ArgumentMatchers.eq(tossPayment)
        );
    }

    @Test
    void handleTossWebhook_ignoresUnsupportedEventType() {
        PaymentWebhookService service = service();

        service.handleTossWebhook(new TossPaymentWebhookRequest(
            "UNKNOWN",
            "2026-08-04T11:20:00.123456",
            paymentData("DONE")
        ));

        verify(tossPaymentClient, never()).getPayment(any());
        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    @Test
    void handleTossWebhook_doesNotFinalizeUnsupportedTossStatus() {
        PaymentWebhookService service = service();
        TossPaymentWebhookRequest request = webhook("READY");
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment("READY"));

        service.handleTossWebhook(request);

        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    @Test
    void handleTossWebhook_failsWhenWebhookAndTossOrderIdMismatch() {
        PaymentWebhookService service = service();
        given(tossPaymentClient.getPayment("payment-key"))
            .willReturn(new TossConfirmResponse(
                "payment-key",
                "OTHER-ORDER",
                BigDecimal.valueOf(10000),
                "DONE",
                "CARD",
                requestedAt(),
                approvedAt()
            ));

        assertBusinessException(
            () -> service.handleTossWebhook(webhook("DONE")),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );

        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    @Test
    void handleTossWebhook_mapsTossClientException() {
        PaymentWebhookService service = service();
        given(tossPaymentClient.getPayment("payment-key"))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        assertBusinessException(
            () -> service.handleTossWebhook(webhook("DONE")),
            GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
        );
    }

    @Test
    void handleTossWebhook_mapsFinalizerLockException() {
        PaymentWebhookService service = service();
        TossConfirmResponse tossPayment = tossPayment("DONE");
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);
        given(paymentFinalizer.finalizePaymentFromWebhook(any(), any()))
            .willThrow(new CannotAcquireLockException("lock timeout"));

        assertBusinessException(
            () -> service.handleTossWebhook(webhook("DONE")),
            GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT
        );
    }

    private PaymentWebhookService service() {
        return new PaymentWebhookService(
            tossPaymentClient,
            paymentFinalizer,
            exceptionTranslator
        );
    }

    private TossPaymentWebhookRequest webhook(String status) {
        return new TossPaymentWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            "2026-08-04T11:20:00.123456",
            paymentData(status)
        );
    }

    private TossPaymentWebhookRequest.PaymentData paymentData(String status) {
        return new TossPaymentWebhookRequest.PaymentData(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            status,
            "CARD",
            requestedAt(),
            "DONE".equals(status) ? approvedAt() : null
        );
    }

    private TossConfirmResponse tossPayment(String status) {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            status,
            "CARD",
            requestedAt(),
            "DONE".equals(status) ? approvedAt() : null
        );
    }

    private OffsetDateTime requestedAt() {
        return OffsetDateTime.parse("2026-08-03T10:00:00+09:00");
    }

    private OffsetDateTime approvedAt() {
        return OffsetDateTime.parse("2026-08-03T10:01:00+09:00");
    }

    private void assertBusinessException(
            Runnable runnable,
            GlobalErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
