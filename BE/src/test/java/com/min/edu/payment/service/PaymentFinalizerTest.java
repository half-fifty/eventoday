package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

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
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.config.PaymentFinalizationProperties;
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
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.outbox.service.PaymentOutboxWriter;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.advertisement.repository.AdvertisementRepository;
import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;

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
    private PaymentVirtualAccountRepository virtualAccountRepository;

    @Mock
    private TicketExchangeCodeIssuer ticketExchangeCodeIssuer;

    @Mock
    private AdvertisementRepository advertisementRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PaymentAuditLogWriter auditLogWriter;

    @Mock
    private PaymentOutboxWriter paymentOutboxWriter;

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
            virtualAccountRepository,
            ticketExchangeCodeIssuer,
            advertisementRepository,
            eventRepository,
            auditLogWriter,
            paymentOutboxWriter
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
            paymentFinalizer.finalizePayment(10L, request(), tossResponse());

        assertThat(response.getPaymentId()).isEqualTo(5L);
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.CONFIRMED.name());
        assertThat(ticketOrder.getConfirmedAt()).isEqualTo(tossResponse().approvedAt());
        assertThat(ticketOrder.getUpdatedAt()).isAfter(tossResponse().approvedAt());
        verify(ticketExchangeCodeIssuer).issueIfAbsent(any(), any(), any());
        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_PAID),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PAID.name()),
            eq(PaymentAuditSource.CONFIRM),
            eq(null),
            eq(PaymentAuditActorType.MEMBER),
            eq(10L),
            eq(null),
            any()
        );
        verify(query).setParameter("timeout", "300ms");
    }

    @Test
    void finalizePayment_appendsGuestReservationConfirmationOutboxForNewGuestPayment() {
        PaymentOrder paymentOrder = pendingGuestPaymentOrder();
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
        given(eventRepository.findById(3L)).willReturn(Optional.of(event()));

        paymentFinalizer.finalizePayment(null, request(), tossResponse());

        verify(paymentOutboxWriter).appendTicketReservationConfirmation(
            "ORDER-1",
            "guest@example.com",
            "test-event"
        );
        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_PAID),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PAID.name()),
            eq(PaymentAuditSource.CONFIRM),
            eq(null),
            eq(PaymentAuditActorType.GUEST),
            eq(null),
            eq(null),
            any()
        );
    }

    @Test
    void finalizePayment_confirmsTicketOrder_publishesFunnelCompletePayment() {
        PaymentOrder paymentOrder = pendingPaymentOrder();
        TicketOrder ticketOrder = TicketOrder.builder()
            .id(2L)
            .paymentOrderId(1L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(TicketOrderStatus.PENDING_PAYMENT.name())
            .funnelSessionId("session-1")
            .funnelAnonymousId("anon-1")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Payment savedPayment = payment();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(savedPayment);

        paymentFinalizer.finalizePayment(10L, request(), tossResponse());

        verify(paymentOutboxWriter).appendFunnelCompletePayment(
            eq("session-1"),
            eq(3L),
            eq("anon-1"),
            eq(10L),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void finalizePayment_doesNotAppendReservationConfirmationOutboxForMemberPayment() {
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

        paymentFinalizer.finalizePayment(10L, request(), tossResponse());

        verify(paymentOutboxWriter, never()).appendTicketReservationConfirmation(any(), any(), any());
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
            paymentFinalizer.finalizePayment(10L, request(), tossResponse());

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
        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_PAID),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PAID.name()),
            eq(PaymentAuditSource.WEBHOOK),
            eq(null),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
    }

    @Test
    void finalizePaymentFromReconciliation_recordsSystemActor() {
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

        paymentFinalizer.finalizePaymentFromReconciliation(request(), tossResponse());

        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_PAID),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PAID.name()),
            eq(PaymentAuditSource.RECONCILIATION),
            eq(null),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
    }

    @Test
    void finalizePaymentFromExpiration_recordsSystemActor() {
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

        paymentFinalizer.finalizePaymentFromExpiration(request(), tossResponse());

        verify(auditLogWriter).append(
            eq(1L),
            eq(5L),
            eq(null),
            eq(PaymentAuditEventType.PAYMENT_PAID),
            eq(PaymentOrderStatus.PENDING.name()),
            eq(PaymentOrderStatus.PAID.name()),
            eq(PaymentAuditSource.EXPIRATION),
            eq(null),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
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
            paymentFinalizer.finalizePayment(10L, request(), tossResponse());

        assertThat(response.getPaymentId()).isEqualTo(5L);
        verify(paymentOutboxWriter, never()).appendTicketReservationConfirmation(any(), any(), any());
        verifyNoInteractions(auditLogWriter);
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

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(10L, request(), tossResponse()))
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

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(10L, request(), tossResponse()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
    }

    @Test
    void finalizePayment_rejectsNonDoneTossResponseBeforeSavingPayment() {
        PaymentOrder paymentOrder = pendingPaymentOrder();
        TicketOrder ticketOrder = pendingTicketOrder();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(10L, request(), new TossConfirmResponse(
                    "payment-key",
                    "ORDER-1",
                    BigDecimal.valueOf(10000),
                    "WAITING_FOR_DEPOSIT",
                    "CARD",
                    tossResponse().requestedAt(),
                    null
                )
            ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(ticketExchangeCodeIssuer, never()).issueIfAbsent(any(), any(), any());
    }

    @Test
    void finalizePayment_rejectsMismatchedTossPaymentKeyBeforeSavingPayment() {
        PaymentOrder paymentOrder = pendingPaymentOrder();
        TicketOrder ticketOrder = pendingTicketOrder();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(10L, request(), new TossConfirmResponse(
                    "other-payment-key",
                    "ORDER-1",
                    BigDecimal.valueOf(10000),
                    "DONE",
                    "CARD",
                    tossResponse().requestedAt(),
                    tossResponse().approvedAt()
                )
            ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(ticketExchangeCodeIssuer, never()).issueIfAbsent(any(), any(), any());
    }

    @Test
    void finalizePayment_marksEventAdvertisementPaid() {
        PaymentOrder paymentOrder = eventAdPaymentOrder();
        Advertisement advertisement = advertisement(AdvertisementStatus.PAYMENT_PENDING);

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(advertisementRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(advertisement));
        given(paymentRepository.findByPaymentOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willReturn(payment());

        paymentFinalizer.finalizePayment(10L, request(), tossResponse());

        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(advertisement.getStatus()).isEqualTo(AdvertisementStatus.PAID);
    }

    @Test
    void finalizePayment_rejectsEventAdvertisementThatIsNotPaymentPendingBeforeSavingPayment() {
        PaymentOrder paymentOrder = eventAdPaymentOrder();
        Advertisement advertisement = advertisement(AdvertisementStatus.REVIEW_PENDING);

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(advertisementRepository.findByPaymentOrderId(1L)).willReturn(Optional.of(advertisement));

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(10L, request(), tossResponse()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_INVALID_STATE);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void finalizePayment_marksVirtualAccountDepositedWithTossDoneStatus() {
        PaymentOrder paymentOrder = waitingVirtualAccountOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        Payment waitingPayment = waitingVirtualAccountPayment();
        PaymentVirtualAccount virtualAccount = virtualAccount();

        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(false);
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(paymentRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(waitingPayment));
        given(paymentRepository.saveAndFlush(waitingPayment)).willReturn(waitingPayment);
        given(virtualAccountRepository.findByPaymentId(5L))
            .willReturn(Optional.of(virtualAccount));

        paymentFinalizer.finalizePaymentFromWebhook(
            request(),
            virtualAccountDoneTossResponse()
        );

        assertThat(waitingPayment.getStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(virtualAccount.getTossStatus()).isEqualTo("DONE");
        assertThat(virtualAccount.getDepositedAt())
            .isEqualTo(virtualAccountDoneTossResponse().approvedAt());
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

    private Payment waitingVirtualAccountPayment() {
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

    private TossConfirmResponse virtualAccountDoneTossResponse() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            "VIRTUAL_ACCOUNT",
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-03T10:01:00+09:00")
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

    private PaymentOrder pendingPaymentOrder() {
        return paymentOrder(PaymentOrderStatus.PENDING);
    }

    private PaymentOrder paidPaymentOrder() {
        return paymentOrder(PaymentOrderStatus.PAID);
    }

    private PaymentOrder waitingVirtualAccountOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOrder pendingGuestPaymentOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(null)
            .buyerName("guest")
            .buyerEmail("guest@example.com")
            .buyerPhone("010-1234-5678")
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
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

    private PaymentOrder eventAdPaymentOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_AD)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Advertisement advertisement(AdvertisementStatus status) {
        return Advertisement.builder()
            .id(7L)
            .eventId(3L)
            .applicantOrganizationId(4L)
            .paymentOrderId(1L)
            .bannerFileId(8L)
            .adText("event ad")
            .startAt(OffsetDateTime.now().plusDays(1))
            .endAt(OffsetDateTime.now().plusDays(10))
            .status(status)
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

    private Event event() {
        return Event.builder()
            .id(3L)
            .organizerOrganizationId(4L)
            .name("test-event")
            .build();
    }
}
