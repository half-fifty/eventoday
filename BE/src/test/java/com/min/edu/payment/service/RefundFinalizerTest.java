package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossCancelResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class RefundFinalizerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRefundRepository paymentRefundRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private TicketInventoryGateway ticketInventoryGateway;

    private RefundFinalizer refundFinalizer;

    @BeforeEach
    void setUp() {
        PaymentFinalizationProperties properties = new PaymentFinalizationProperties();
        properties.setFinalizationLockTimeoutMs(300L);
        properties.setWebhookFinalizationLockTimeoutMs(150L);
        refundFinalizer = new RefundFinalizer(
            entityManager,
            properties,
            paymentOrderRepository,
            paymentRepository,
            ticketOrderRepository,
            paymentRefundRepository,
            exchangeCodeRepository,
            ticketInventoryGateway,
            new EventOperationDeadlinePolicy()
        );

        given(entityManager.createNativeQuery(any(String.class))).willReturn(query);
        given(query.setParameter(eq("timeout"), any())).willReturn(query);
        given(query.getSingleResult()).willReturn("");
    }

    @Test
    void finalizeRefund_completesWhenRequestedBeforeCutoffEvenIfFinalizerRunsAfterCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        RefundPaymentProjection projection = projection(
            PaymentStatus.PAID.name(),
            PaymentOrderStatus.PAID.name(),
            TicketOrderStatus.CONFIRMED.name(),
            now.minusHours(2),
            now.plusMinutes(30)
        );
        PaymentOrder paymentOrder = paidPaymentOrder();
        Payment payment = paidPayment();
        TicketOrder ticketOrder = confirmedTicketOrder();
        PaymentRefund refund = requestedRefund(now.minusHours(1));
        List<ExchangeCode> exchangeCodes = List.of(
            ExchangeCode.createForTicketOrder(3L, 2L, 10L, "code-1", null, now.minusHours(1)),
            ExchangeCode.createForTicketOrder(3L, 2L, 10L, "code-2", null, now.minusHours(1))
        );

        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(projection));
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(ticketOrderRepository.findByPaymentOrderId(11L)).willReturn(Optional.of(ticketOrder));
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(any(), any())).willReturn(false);
        given(exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(2L))
            .willReturn(exchangeCodes);
        given(ticketInventoryGateway.release(3L, 2)).willReturn(true);
        given(paymentRefundRepository.saveAndFlush(refund)).willReturn(refund);

        CreateRefundResponse response = refundFinalizer.finalizeRefund(
            7L,
            1L,
            10L,
            new CreateRefundRequest("reason"),
            tossResponse()
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        assertThat(payment.isRefunded()).isTrue();
        assertThat(paymentOrder.isRefunded()).isTrue();
        assertThat(ticketOrder.isRefunded()).isTrue();
        assertThat(refund.isCompleted()).isTrue();
        assertThat(exchangeCodes).allMatch(ExchangeCode::isCancelled);
    }

    @Test
    void finalizeRefund_failsAtOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        RefundPaymentProjection projection = projection(
            PaymentStatus.PAID.name(),
            PaymentOrderStatus.PAID.name(),
            TicketOrderStatus.CONFIRMED.name(),
            now.minusHours(2),
            now.plusHours(1)
        );
        PaymentOrder paymentOrder = paidPaymentOrder();
        Payment payment = paidPayment();
        TicketOrder ticketOrder = confirmedTicketOrder();
        PaymentRefund refund = requestedRefund();

        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(projection));
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(ticketOrderRepository.findByPaymentOrderId(11L)).willReturn(Optional.of(ticketOrder));
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundFinalizer.finalizeRefund(
            7L,
            1L,
            10L,
            new CreateRefundRequest("reason"),
            tossResponse()
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_NOT_ALLOWED);

        verify(exchangeCodeRepository, never()).existsByTicketOrderIdAndStatus(any(), any());
        verifyNoInteractions(ticketInventoryGateway);
        verify(paymentRefundRepository, never()).saveAndFlush(any());
    }

    @Test
    void finalizeRefund_failsAfterOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        RefundPaymentProjection projection = projection(
            PaymentStatus.PAID.name(),
            PaymentOrderStatus.PAID.name(),
            TicketOrderStatus.CONFIRMED.name(),
            now.minusHours(2),
            now.plusMinutes(30)
        );
        PaymentOrder paymentOrder = paidPaymentOrder();
        Payment payment = paidPayment();
        TicketOrder ticketOrder = confirmedTicketOrder();
        PaymentRefund refund = requestedRefund();

        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(projection));
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(ticketOrderRepository.findByPaymentOrderId(11L)).willReturn(Optional.of(ticketOrder));
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundFinalizer.finalizeRefund(
            7L,
            1L,
            10L,
            new CreateRefundRequest("reason"),
            tossResponse()
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_NOT_ALLOWED);

        verify(exchangeCodeRepository, never()).existsByTicketOrderIdAndStatus(any(), any());
        verifyNoInteractions(ticketInventoryGateway);
        verify(paymentRefundRepository, never()).saveAndFlush(any());
    }

    @Test
    void finalizeRefund_returnsCompletedRefundWithoutRepeatingSideEffects() {
        RefundPaymentProjection projection = projection();
        PaymentOrder paymentOrder = refundedPaymentOrder();
        Payment payment = refundedPayment();
        TicketOrder ticketOrder = refundedTicketOrder();
        PaymentRefund refund = completedRefund();

        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(projection));
        given(paymentOrderRepository.findByOrderNoForUpdate("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(ticketOrderRepository.findByPaymentOrderId(11L)).willReturn(Optional.of(ticketOrder));
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));

        CreateRefundResponse response = refundFinalizer.finalizeRefund(
            7L,
            1L,
            10L,
            new CreateRefundRequest("reason"),
            tossResponse()
        );

        assertThat(response.getRefundId()).isEqualTo(7L);
        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(exchangeCodeRepository, never()).findAllByTicketOrderIdOrderByIdAsc(any());
        verifyNoInteractions(ticketInventoryGateway);
        verify(paymentRefundRepository, never()).saveAndFlush(any());
    }

    private RefundPaymentProjection projection() {
        return projection(
            PaymentStatus.REFUNDED.name(),
            PaymentOrderStatus.REFUNDED.name(),
            TicketOrderStatus.REFUNDED.name(),
            OffsetDateTime.now().plusDays(1),
            OffsetDateTime.now().plusDays(2)
        );
    }

    private RefundPaymentProjection projection(
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime eventStartAt,
            OffsetDateTime eventEndAt) {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return paymentStatus; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return 10L; }
            @Override public BigDecimal getTotalAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentOrderStatus() { return paymentOrderStatus; }
            @Override public Long getTicketOrderId() { return 2L; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event"; }
            @Override public OffsetDateTime getEventStartAt() { return eventStartAt; }
            @Override public OffsetDateTime getEventEndAt() { return eventEndAt; }
            @Override public Integer getQuantity() { return 2; }
            @Override public String getTicketOrderStatus() { return ticketOrderStatus; }
        };
    }

    private PaymentOrder paidPaymentOrder() {
        return PaymentOrder.builder()
            .id(11L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PAID.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Payment paidPayment() {
        return Payment.builder()
            .id(1L)
            .paymentOrderId(11L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("CARD")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.PAID.name())
            .requestedAt(OffsetDateTime.now())
            .approvedAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private TicketOrder confirmedTicketOrder() {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(11L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(2)
            .status(TicketOrderStatus.CONFIRMED.name())
            .confirmedAt(OffsetDateTime.now())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOrder refundedPaymentOrder() {
        return PaymentOrder.builder()
            .id(11L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.REFUNDED.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Payment refundedPayment() {
        return Payment.builder()
            .id(1L)
            .paymentOrderId(11L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("CARD")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.REFUNDED.name())
            .requestedAt(OffsetDateTime.now())
            .approvedAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private TicketOrder refundedTicketOrder() {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(11L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(2)
            .status(TicketOrderStatus.REFUNDED.name())
            .confirmedAt(OffsetDateTime.now())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentRefund completedRefund() {
        PaymentRefund refund = requestedRefund();
        refund.complete("cancel-key", OffsetDateTime.now());
        return refund;
    }

    private PaymentRefund requestedRefund() {
        return requestedRefund(OffsetDateTime.now());
    }

    private PaymentRefund requestedRefund(OffsetDateTime requestedAt) {
        return PaymentRefund.builder()
            .id(7L)
            .paymentId(1L)
            .requesterMemberId(10L)
            .refundAmount(BigDecimal.valueOf(10000))
            .reason("reason")
            .status(com.min.edu.payment.domain.PaymentRefundStatus.REQUESTED)
            .requestedAt(requestedAt)
            .build();
    }

    private TossCancelResponse tossResponse() {
        return new TossCancelResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "CANCELED",
            "CARD",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            java.util.List.of(new TossCancelResponse.Cancel(
                "cancel-key",
                BigDecimal.valueOf(10000),
                "reason",
                OffsetDateTime.now()
            ))
        );
    }
}
