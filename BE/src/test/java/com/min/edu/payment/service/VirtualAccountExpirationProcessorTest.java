package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private TicketInventoryGateway ticketInventoryGateway;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    @Test
    void process_returnsWithoutExpiringWhenLatestPaymentAfterOrderLockIsPaid() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");

        given(virtualAccountRepository.findById(7L))
            .willReturn(Optional.of(virtualAccount()));
        given(paymentOrderRepository.findByVirtualAccountIdForUpdate(7L))
            .willReturn(Optional.of(waitingOrder()));
        given(paymentRepository.findByPaymentOrderIdForUpdate(1L))
            .willReturn(Optional.of(payment(PaymentStatus.PAID)));

        processor.process(7L, now);

        verify(ticketOrderRepository, never()).findByPaymentOrderId(any());
        verify(tossPaymentClient, never()).getPayment(any());
        verify(ticketInventoryGateway, never()).release(anyLong(), anyInt());
    }

    @Test
    void process_expiresAndReleasesInventoryOnlyWhenLatestPaymentIsWaiting() {
        VirtualAccountExpirationProcessor processor = processor();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:31:00+09:00");
        PaymentOrder order = waitingOrder();
        Payment waitingPayment = payment(PaymentStatus.WAITING_FOR_DEPOSIT);
        TicketOrder ticketOrder = pendingTicketOrder();
        PaymentVirtualAccount virtualAccount = virtualAccount();

        given(virtualAccountRepository.findById(7L))
            .willReturn(Optional.of(virtualAccount));
        given(paymentOrderRepository.findByVirtualAccountIdForUpdate(7L))
            .willReturn(Optional.of(order));
        given(paymentRepository.findByPaymentOrderIdForUpdate(1L))
            .willReturn(Optional.of(waitingPayment));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(tossPaymentClient.getPayment("payment-key"))
            .willReturn(tossPayment("WAITING_FOR_DEPOSIT"));
        given(exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(2L))
            .willReturn(List.of());
        given(ticketInventoryGateway.release(3L, 2)).willReturn(true);

        processor.process(7L, now);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED.name());
        assertThat(waitingPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED.name());
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.EXPIRED.name());
        assertThat(virtualAccount.getTossStatus()).isEqualTo("EXPIRED");
        verify(ticketInventoryGateway, times(1)).release(3L, 2);
    }

    private VirtualAccountExpirationProcessor processor() {
        return new VirtualAccountExpirationProcessor(
            virtualAccountRepository,
            paymentRepository,
            paymentOrderRepository,
            ticketOrderRepository,
            exchangeCodeRepository,
            ticketInventoryGateway,
            tossPaymentClient,
            paymentFinalizer
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

    private PaymentOrder waitingOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name())
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

    private TicketOrder pendingTicketOrder() {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(1L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(5000))
            .totalQuantity(2)
            .status(TicketOrderStatus.PENDING_PAYMENT.name())
            .createdAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
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
