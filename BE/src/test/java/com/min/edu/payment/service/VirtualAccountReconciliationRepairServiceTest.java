package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualAccountReconciliationRepairServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentVirtualAccountRepository virtualAccountRepository;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    @Mock
    private PaymentAuditLogWriter auditLogWriter;

    private VirtualAccountReconciliationRepairService service;

    @BeforeEach
    void setUp() {
        service = new VirtualAccountReconciliationRepairService(
            paymentOrderRepository,
            ticketOrderRepository,
            paymentRepository,
            virtualAccountRepository,
            paymentFinalizer,
            auditLogWriter
        );
    }

    @Test
    void repairPendingOrder_recoversWaitingForDepositLocalState() {
        PaymentOrder order = pendingOrder();
        Payment savedPayment = waitingPayment();
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1")).willReturn(Optional.of(order));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(ticketOrderRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(pendingTicketOrder()));
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);
        given(virtualAccountRepository.save(any(PaymentVirtualAccount.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.repairPendingOrder("ORDER-1", waitingProviderPayment());

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name());
        ArgumentCaptor<PaymentVirtualAccount> vaCaptor =
            ArgumentCaptor.forClass(PaymentVirtualAccount.class);
        verify(virtualAccountRepository).save(vaCaptor.capture());
        assertThat(vaCaptor.getValue().getTossStatus()).isEqualTo("WAITING_FOR_DEPOSIT");
        verify(paymentFinalizer, never()).finalizePaymentFromReconciliation(any(), any());
        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_WAITING_FOR_DEPOSIT),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name()),
            eq(PaymentAuditSource.RECONCILIATION),
            eq("VA_LOCAL_STATE_RECOVERED"),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
    }

    @Test
    void repairPendingOrder_recoversWaitingStateThenDelegatesDoneFinalization() {
        PaymentOrder order = pendingOrder();
        Payment savedPayment = waitingPayment();
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1")).willReturn(Optional.of(order));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(ticketOrderRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(pendingTicketOrder()));
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);
        given(virtualAccountRepository.save(any(PaymentVirtualAccount.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.repairPendingOrder("ORDER-1", doneProviderPayment());

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name());
        verify(paymentFinalizer).finalizePaymentFromReconciliation(
            any(ConfirmPaymentRequest.class),
            eq(doneProviderPayment())
        );
    }

    @Test
    void repairPendingOrder_noopsWhenWebhookAlreadyPaidOrderFirst() {
        PaymentOrder order = paidOrder();
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1")).willReturn(Optional.of(order));

        service.repairPendingOrder("ORDER-1", doneProviderPayment());

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(virtualAccountRepository, never()).save(any());
        verify(paymentFinalizer, never()).finalizePaymentFromReconciliation(any(), any());
        verify(auditLogWriter, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repairPendingOrder_recordsInvestigateForUnsupportedProviderStatus() {
        PaymentOrder order = pendingOrder();
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1")).willReturn(Optional.of(order));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());

        service.repairPendingOrder("ORDER-1", providerPayment("CANCELED", null));

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(auditLogWriter).append(
            eq(1L),
            eq(null),
            eq(null),
            eq(PaymentAuditEventType.VA_RECONCILIATION_INVESTIGATE),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentAuditSource.RECONCILIATION),
            eq("UNSUPPORTED_PROVIDER_STATUS_CANCELED"),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
    }

    private PaymentOrder pendingOrder() {
        return paymentOrder(PaymentOrderStatus.PENDING);
    }

    private PaymentOrder paidOrder() {
        return paymentOrder(PaymentOrderStatus.PAID);
    }

    private PaymentOrder paymentOrder(PaymentOrderStatus status) {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(status.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now().minusMinutes(2))
            .updatedAt(OffsetDateTime.now().minusMinutes(2))
            .build();
    }

    private TicketOrder pendingTicketOrder() {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(1L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(TicketOrderStatus.PENDING_PAYMENT.name())
            .createdAt(OffsetDateTime.now().minusMinutes(2))
            .updatedAt(OffsetDateTime.now().minusMinutes(2))
            .build();
    }

    private Payment waitingPayment() {
        return Payment.builder()
            .id(5L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("VIRTUAL_ACCOUNT")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.WAITING_FOR_DEPOSIT.name())
            .requestedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .build();
    }

    private TossConfirmResponse waitingProviderPayment() {
        return providerPayment("WAITING_FOR_DEPOSIT", null);
    }

    private TossConfirmResponse doneProviderPayment() {
        return providerPayment("DONE", OffsetDateTime.parse("2026-08-03T10:01:00+09:00"));
    }

    private TossConfirmResponse providerPayment(String status, OffsetDateTime approvedAt) {
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
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            approvedAt
        );
    }
}
