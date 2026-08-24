package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.advertisement.repository.AdvertisementRepository;
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
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualAccountPaymentServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentVirtualAccountRepository virtualAccountRepository;

    @Mock
    private PaymentAuditLogWriter auditLogWriter;

    @Mock
    private AdvertisementRepository advertisementRepository;

    @Test
    void saveWaitingForDeposit_storesTossDueDateAsKoreaOffsetDateTime() {
        VirtualAccountPaymentService service = new VirtualAccountPaymentService(
            paymentOrderRepository,
            ticketOrderRepository,
            paymentRepository,
            virtualAccountRepository,
            auditLogWriter,
            advertisementRepository
        );
        PaymentOrder paymentOrder = pendingVirtualAccountOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment savedPayment = waitingPayment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);
        given(virtualAccountRepository.save(any(PaymentVirtualAccount.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.saveWaitingForDeposit(
            10L,
            paymentOrder,
            "payment-key",
            virtualAccountTossResponse()
        );

        ArgumentCaptor<PaymentVirtualAccount> captor =
            ArgumentCaptor.forClass(PaymentVirtualAccount.class);
        verify(virtualAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getDueAt())
            .isEqualTo(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"));
        assertThat(captor.getValue().getTossStatus()).isEqualTo("WAITING_FOR_DEPOSIT");
        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_WAITING_FOR_DEPOSIT),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name()),
            eq(PaymentAuditSource.CONFIRM),
            eq(null),
            eq(PaymentAuditActorType.MEMBER),
            eq(10L),
            eq(null),
            any()
        );
    }

    @Test
    void saveWaitingForDeposit_recordsGuestActorWhenRequesterMemberIdIsNull() {
        VirtualAccountPaymentService service = new VirtualAccountPaymentService(
            paymentOrderRepository,
            ticketOrderRepository,
            paymentRepository,
            virtualAccountRepository,
            auditLogWriter,
            advertisementRepository
        );
        PaymentOrder paymentOrder = pendingGuestVirtualAccountOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment savedPayment = waitingPayment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);
        given(virtualAccountRepository.save(any(PaymentVirtualAccount.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.saveWaitingForDeposit(
            null,
            paymentOrder,
            "payment-key",
            virtualAccountTossResponse()
        );

        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_WAITING_FOR_DEPOSIT),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name()),
            eq(PaymentAuditSource.CONFIRM),
            eq(null),
            eq(PaymentAuditActorType.GUEST),
            eq(null),
            eq(null),
            any()
        );
    }

    @Test
    void saveWaitingForDeposit_rejectsVirtualAccountResponseWithoutBankCode() {
        VirtualAccountPaymentService service = new VirtualAccountPaymentService(
            paymentOrderRepository,
            ticketOrderRepository,
            paymentRepository,
            virtualAccountRepository,
            auditLogWriter,
            advertisementRepository
        );
        PaymentOrder paymentOrder = pendingVirtualAccountOrder();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveWaitingForDeposit(
            10L,
            paymentOrder,
                "payment-key",
                virtualAccountTossResponseWithoutBankCode()
            ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.VIRTUAL_ACCOUNT_REQUIRED);
    }

    private TossConfirmResponse virtualAccountTossResponse() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            "secret",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                "088",
                "tester",
                OffsetDateTime.parse("2026-08-03T10:30:00+09:00")
            ),
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            null
        );
    }

    private TossConfirmResponse virtualAccountTossResponseWithoutBankCode() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            "secret",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                null,
                "tester",
                OffsetDateTime.parse("2026-08-03T10:30:00+09:00")
            ),
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            null
        );
    }

    private PaymentOrder pendingVirtualAccountOrder() {
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

    private PaymentOrder pendingGuestVirtualAccountOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(null)
            .buyerName("guest")
            .buyerEmail("guest@example.com")
            .buyerPhone("010-1234-5678")
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"))
            .createdAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
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
            .createdAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
            .updatedAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"))
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
}
