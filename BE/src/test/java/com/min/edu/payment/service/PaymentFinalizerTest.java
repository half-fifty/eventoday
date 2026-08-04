package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class PaymentFinalizerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TicketExchangeCodeIssuer ticketExchangeCodeIssuer;

    private PaymentFinalizer paymentFinalizer;

    @BeforeEach
    void setUp() {
        PaymentFinalizationProperties properties = new PaymentFinalizationProperties();
        properties.setFinalizationLockTimeoutMs(300L);
        properties.setWebhookFinalizationLockTimeoutMs(150L);
        paymentFinalizer = new PaymentFinalizer(
            entityManager,
            properties,
            paymentOrderRepository,
            ticketOrderRepository,
            paymentRepository,
            ticketExchangeCodeIssuer
        );

        given(entityManager.createNativeQuery(any(String.class))).willReturn(query);
        given(query.setParameter(eq("timeout"), any())).willReturn(query);
        given(query.getSingleResult()).willReturn("");
    }

    @Test
    void finalizePayment_savesPaymentAndConfirmsOrders() {
        PaymentOrder paymentOrder = pendingPaymentOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment savedPayment = payment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);

        ConfirmPaymentResponse response =
            paymentFinalizer.finalizePayment(request(), tossResponse());

        assertThat(response.getPaymentId()).isEqualTo(5L);
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.CONFIRMED.name());
        assertThat(ticketOrder.getConfirmedAt()).isEqualTo(tossResponse().approvedAt());
        assertThat(ticketOrder.getUpdatedAt()).isAfter(tossResponse().approvedAt());
        verify(ticketExchangeCodeIssuer).issueIfAbsent(any(), any(), any());
        verify(query).setParameter("timeout", "300ms");
    }

    @Test
    void finalizePayment_succeedsWhenOrderExpiresAfterTossApproved() {
        PaymentOrder paymentOrder = expiredPaymentOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment savedPayment = payment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);

        ConfirmPaymentResponse response =
            paymentFinalizer.finalizePayment(request(), tossResponse());

        assertThat(response.getPaymentId()).isEqualTo(5L);
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.CONFIRMED.name());
    }

    @Test
    void finalizePaymentFromWebhook_usesWebhookLockTimeout() {
        PaymentOrder paymentOrder = pendingPaymentOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment savedPayment = payment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);

        paymentFinalizer.finalizePaymentFromWebhook(request(), tossResponse());

        verify(query).setParameter("timeout", "150ms");
    }

    @Test
    void finalizePayment_returnsExistingPaymentForSamePaymentKey() {
        PaymentOrder paymentOrder = paidPaymentOrder();
        TicketOrder ticketOrder = confirmedTicketOrder();
        Payment payment = payment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(payment));

        ConfirmPaymentResponse response =
            paymentFinalizer.finalizePayment(request(), tossResponse());

        assertThat(response.getPaymentId()).isEqualTo(5L);
    }

    @Test
    void finalizePayment_failsWhenPaidOrderHasDifferentPaymentKey() {
        PaymentOrder paymentOrder = paidPaymentOrder();
        TicketOrder ticketOrder = confirmedTicketOrder();
        Payment payment = Payment.builder()
            .id(5L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("other-key")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.PAID.name())
            .requestedAt(OffsetDateTime.now())
            .approvedAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(request(), tossResponse()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_ALREADY_PROCESSED);
    }

    @Test
    void finalizePayment_failsWhenPaymentKeyIsUsedByDifferentOrder() {
        PaymentOrder paymentOrder = pendingPaymentOrder();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(true);

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(request(), tossResponse()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
    }

    private ConfirmPaymentRequest request() {
        return new ConfirmPaymentRequest(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000)
        );
    }

    private TossConfirmResponse tossResponse() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            "CARD",
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-03T10:01:00+09:00")
        );
    }

    private Payment payment() {
        return Payment.builder()
            .id(5L)
            .paymentOrderId(1L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("CARD")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.PAID.name())
            .requestedAt(tossResponse().requestedAt())
            .approvedAt(tossResponse().approvedAt())
            .updatedAt(tossResponse().approvedAt())
            .build();
    }

    private PaymentOrder pendingPaymentOrder() {
        return paymentOrder(PaymentOrderStatus.PENDING);
    }

    private PaymentOrder paidPaymentOrder() {
        return paymentOrder(PaymentOrderStatus.PAID);
    }

    private PaymentOrder expiredPaymentOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().minusSeconds(1))
            .createdAt(OffsetDateTime.now().minusMinutes(10))
            .updatedAt(OffsetDateTime.now().minusMinutes(10))
            .build();
    }

    private PaymentOrder paymentOrder(PaymentOrderStatus status) {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(status.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private TicketOrder pendingTicketOrder() {
        return ticketOrder(TicketOrderStatus.PENDING_PAYMENT);
    }

    private TicketOrder confirmedTicketOrder() {
        return ticketOrder(TicketOrderStatus.CONFIRMED);
    }

    private TicketOrder ticketOrder(TicketOrderStatus status) {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(1L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(status.name())
            .confirmedAt(status == TicketOrderStatus.CONFIRMED ? OffsetDateTime.now() : null)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
