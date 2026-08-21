package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
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

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentVirtualAccountRepository virtualAccountRepository;

    private final PaymentFinalizationExceptionTranslator exceptionTranslator =
        new PaymentFinalizationExceptionTranslator();

    @Test
    void handleTossWebhook_finalizesDonePayment() {
        PaymentWebhookService service = service();
        TossPaymentWebhookRequest request = webhook("DONE");
        TossConfirmResponse tossPayment = tossPayment("DONE");
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(virtualAccountOrder()));

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
    void handleTossWebhook_ignoresLateWaitingForDepositAfterLocalPaymentIsPaid() {
        PaymentWebhookService service = service();
        Payment paidPayment = Payment.builder()
            .id(2L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("VIRTUAL_ACCOUNT")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.PAID.name())
            .requestedAt(requestedAt())
            .approvedAt(approvedAt())
            .updatedAt(approvedAt())
            .build();
        TossConfirmResponse tossPayment = tossPayment("WAITING_FOR_DEPOSIT");

        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);
        given(paymentRepository.findByPaymentKey("payment-key")).willReturn(Optional.of(paidPayment));

        service.handleTossWebhook(webhook("WAITING_FOR_DEPOSIT"));

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
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(virtualAccountOrder()));
        given(paymentFinalizer.finalizePaymentFromWebhook(any(), any()))
            .willThrow(new CannotAcquireLockException("lock timeout"));

        assertBusinessException(
            () -> service.handleTossWebhook(webhook("DONE")),
            GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT
        );
    }

    @Test
    void handleTossWebhook_rejectsMalformedPaymentStatusChangedPayload() {
        PaymentWebhookService service = service();

        assertBusinessException(
            () -> service.handleTossWebhook(new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                "2026-08-04T11:20:00.123456",
                null
            )),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );

        verify(tossPaymentClient, never()).getPayment(any());
        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    @Test
    void handleTossWebhook_rejectsPaymentStatusChangedWithoutTotalAmount() {
        PaymentWebhookService service = service();

        assertBusinessException(
            () -> service.handleTossWebhook(new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                "2026-08-04T11:20:00.123456",
                new TossPaymentWebhookRequest.PaymentData(
                    "payment-key",
                    "ORDER-1",
                    null,
                    "DONE",
                    "CARD",
                    requestedAt(),
                    approvedAt()
                )
            )),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );

        verify(tossPaymentClient, never()).getPayment(any());
        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    private PaymentWebhookService service() {
        return new PaymentWebhookService(
            tossPaymentClient,
            paymentFinalizer,
            exceptionTranslator,
            paymentOrderRepository,
            paymentRepository,
            virtualAccountRepository
        );
    }

    @Test
    void handleTossWebhook_finalizesDepositCallbackWhenSecretMatches() {
        PaymentWebhookService service = service();
        PaymentOrder paymentOrder = virtualAccountOrder();
        Payment payment = waitingPayment();
        PaymentVirtualAccount virtualAccount = virtualAccount("secret");
        TossConfirmResponse tossPayment = virtualAccountTossPayment("DONE");

        given(paymentOrderRepository.findByOrderNo("ORDER-1")).willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(payment));
        given(virtualAccountRepository.findByPaymentId(2L)).willReturn(Optional.of(virtualAccount));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1")).willReturn(tossPayment);

        service.handleTossWebhook(depositCallback("DONE", "secret"));

        verify(paymentFinalizer).finalizePaymentFromWebhook(
            any(ConfirmPaymentRequest.class),
            org.mockito.ArgumentMatchers.eq(tossPayment)
        );
    }

    @Test
    void handleTossWebhook_rejectsDepositCallbackWhenSecretMismatch() {
        PaymentWebhookService service = service();

        given(paymentOrderRepository.findByOrderNo("ORDER-1")).willReturn(Optional.of(virtualAccountOrder()));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(waitingPayment()));
        given(virtualAccountRepository.findByPaymentId(2L)).willReturn(Optional.of(virtualAccount("secret")));

        assertBusinessException(
            () -> service.handleTossWebhook(depositCallback("DONE", "other-secret")),
            GlobalErrorCode.VIRTUAL_ACCOUNT_SECRET_MISMATCH
        );

        verify(tossPaymentClient, never()).getPaymentByOrderId(any());
        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    @Test
    void handleTossWebhook_ignoresDoneWhenLocalOrderIsRefunded() {
        PaymentWebhookService service = service();
        TossConfirmResponse tossPayment = tossPayment("DONE");

        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(refundedOrder()));

        service.handleTossWebhook(webhook("DONE"));

        verify(paymentFinalizer, never()).finalizePaymentFromWebhook(any(), any());
    }

    private TossPaymentWebhookRequest webhook(String status) {
        return new TossPaymentWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            "2026-08-04T11:20:00.123456",
            paymentData(status)
        );
    }

    private TossPaymentWebhookRequest depositCallback(String status, String secret) {
        return new TossPaymentWebhookRequest(
            "2026-08-04T11:20:00.123456",
            secret,
            status,
            "transaction-key",
            "ORDER-1"
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

    private TossConfirmResponse virtualAccountTossPayment(String status) {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            status,
            "VIRTUAL_ACCOUNT",
            "secret",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                "088",
                "tester",
                OffsetDateTime.parse("2026-08-03T10:30:00+09:00")
            ),
            requestedAt(),
            "DONE".equals(status) ? approvedAt() : null
        );
    }

    private PaymentOrder virtualAccountOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name())
            .expiresAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .createdAt(requestedAt())
            .updatedAt(requestedAt())
            .build();
    }

    private PaymentOrder refundedOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.CARD)
            .status(PaymentOrderStatus.REFUNDED.name())
            .expiresAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .createdAt(requestedAt())
            .updatedAt(requestedAt())
            .build();
    }

    private Payment waitingPayment() {
        return Payment.builder()
            .id(2L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("VIRTUAL_ACCOUNT")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.WAITING_FOR_DEPOSIT.name())
            .requestedAt(requestedAt())
            .updatedAt(requestedAt())
            .build();
    }

    private PaymentVirtualAccount virtualAccount(String secret) {
        return PaymentVirtualAccount.builder()
            .id(3L)
            .paymentId(2L)
            .bankCode("088")
            .accountNumber("1234567890")
            .customerName("tester")
            .dueAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .webhookSecretHash(PaymentSecretHasher.sha256(secret))
            .tossStatus("WAITING_FOR_DEPOSIT")
            .createdAt(requestedAt())
            .updatedAt(requestedAt())
            .build();
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
