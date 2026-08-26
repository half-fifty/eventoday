package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualAccountExpirationProcessorTest {

    @Mock
    private PaymentVirtualAccountRepository virtualAccountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private VirtualAccountExpirationRepairService repairService;

    @Mock
    private VirtualAccountReconciliationRepairService reconciliationRepairService;

    @Test
    void process_returnsWithoutExpiringWhenLatestPaymentAfterOrderLockIsPaid() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");

        given(virtualAccountRepository.findById(7L)).willReturn(Optional.of(virtualAccount()));
        given(paymentRepository.findById(5L)).willReturn(Optional.of(payment(PaymentStatus.PAID)));

        processor.process(7L, now);

        verify(tossPaymentClient, never()).getPayment(any());
        verify(repairService, never()).process(anyLong(), any(), any());
    }

    @Test
    void process_looksUpProviderOutsideRepairAndDelegatesWhenPaymentIsWaiting() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        Payment waitingPayment = payment(PaymentStatus.WAITING_FOR_DEPOSIT);
        PaymentVirtualAccount virtualAccount = virtualAccount();
        TossConfirmResponse tossPayment = tossPayment("WAITING_FOR_DEPOSIT");

        given(virtualAccountRepository.findById(7L)).willReturn(Optional.of(virtualAccount));
        given(paymentRepository.findById(5L)).willReturn(Optional.of(waitingPayment));
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossPayment);

        processor.process(7L, now);

        verify(repairService).process(7L, now, tossPayment);
        verify(paymentOrderRepository, never()).findByVirtualAccountIdForUpdate(anyLong());
    }

    @Test
    void processPendingOrder_delegatesDoneProviderPaymentToReconciliationRepair() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder pendingOrder = pendingOrder();
        TossConfirmResponse tossPayment = tossPayment("DONE");

        given(paymentOrderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1")).willReturn(tossPayment);

        processor.processPendingOrder(1L, now);

        verify(reconciliationRepairService).repairPendingOrder("ORDER-1", tossPayment);
        verify(repairService, never()).expirePendingOrder(anyLong(), any(), any());
    }

    @Test
    void processPendingOrder_delegatesWaitingProviderPaymentToReconciliationRepair() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder pendingOrder = pendingOrder();
        TossConfirmResponse tossPayment = tossPayment("WAITING_FOR_DEPOSIT");

        given(paymentOrderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1")).willReturn(tossPayment);

        processor.processPendingOrder(1L, now);

        verify(reconciliationRepairService).repairPendingOrder("ORDER-1", tossPayment);
        verify(repairService, never()).expirePendingOrder(anyLong(), any(), any());
    }

    @Test
    void processPendingOrder_keepsPendingWhenProviderLookupIsAmbiguous() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder pendingOrder = pendingOrder();

        given(paymentOrderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1"))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        processor.processPendingOrder(1L, now);

        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING.name());
        verify(reconciliationRepairService, never()).repairPendingOrder(any(), any());
        verify(repairService, never()).expirePendingOrder(anyLong(), any(), any());
    }

    @Test
    void processPendingOrder_keepsPendingWhenProviderLookupReturnsInvalidOrNotFoundMappedError() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder pendingOrder = pendingOrder();

        given(paymentOrderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1"))
            .willThrow(new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            ));

        processor.processPendingOrder(1L, now);

        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING.name());
        verify(reconciliationRepairService, never()).repairPendingOrder(any(), any());
        verify(repairService, never()).expirePendingOrder(anyLong(), any(), any());
    }

    @Test
    void processPendingOrder_expiresOnlyForExplicitProviderExpiredStatus() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder pendingOrder = pendingOrder();
        TossConfirmResponse tossPayment = tossPayment("EXPIRED");

        given(paymentOrderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1")).willReturn(tossPayment);

        processor.processPendingOrder(1L, now);

        verify(repairService).expirePendingOrder(1L, now, "EXPIRED");
        verify(reconciliationRepairService, never()).repairPendingOrder(any(), any());
    }

    private VirtualAccountExpirationProcessor processor() {
        return new VirtualAccountExpirationProcessor(
            virtualAccountRepository,
            paymentRepository,
            paymentOrderRepository,
            tossPaymentClient,
            repairService,
            reconciliationRepairService
        );
    }

    private PaymentVirtualAccount virtualAccount() {
        return PaymentVirtualAccount.builder()
            .id(7L)
            .paymentId(5L)
            .bankCode("088")
            .accountNumber("1234567890")
            .customerName("tester")
            .dueAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .webhookSecretHash("hash")
            .tossStatus("WAITING_FOR_DEPOSIT")
            .createdAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .build();
    }

    private PaymentOrder pendingOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .createdAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .build();
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
            .id(5L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("VIRTUAL_ACCOUNT")
            .amount(BigDecimal.valueOf(10000))
            .status(status.name())
            .requestedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .approvedAt(status == PaymentStatus.PAID
                ? OffsetDateTime.parse("2026-08-03T10:01:00+09:00")
                : null)
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .build();
    }

    private TossConfirmResponse tossPayment(String status) {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            status,
            "VIRTUAL_ACCOUNT",
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            null
        );
    }
}
