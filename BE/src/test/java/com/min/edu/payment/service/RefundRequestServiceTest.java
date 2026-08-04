package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.dto.TossCancelResponse;

class RefundRequestServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentRefundRepository paymentRefundRepository;
    private ExchangeCodeRepository exchangeCodeRepository;
    private OrderAccessTokenProvider orderAccessTokenProvider;
    private TossPaymentClient tossPaymentClient;
    private RefundFinalizer refundFinalizer;
    private RefundFinalizationExceptionTranslator exceptionTranslator;
    private RefundRequestService service;

    @BeforeEach
    void setUp() {
        paymentRepository = org.mockito.Mockito.mock(PaymentRepository.class);
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        exchangeCodeRepository = org.mockito.Mockito.mock(ExchangeCodeRepository.class);
        orderAccessTokenProvider = org.mockito.Mockito.mock(OrderAccessTokenProvider.class);
        tossPaymentClient = org.mockito.Mockito.mock(TossPaymentClient.class);
        refundFinalizer = org.mockito.Mockito.mock(RefundFinalizer.class);
        exceptionTranslator = new RefundFinalizationExceptionTranslator();
        service = new RefundRequestService(
            paymentRepository,
            paymentRefundRepository,
            exchangeCodeRepository,
            orderAccessTokenProvider,
            tossPaymentClient,
            refundFinalizer,
            exceptionTranslator
        );
    }

    @Test
    void refund_succeedsForMemberOwner() {
        RefundPaymentProjection payment = projection(10L);
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(
            2L,
            ExchangeCodeStatus.REDEEMED
        )).willReturn(false);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(tossPaymentClient).cancel(any());
    }

    @Test
    void refund_succeedsForGuestWithToken() {
        RefundPaymentProjection payment = projection(null);
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(
            null,
            "token",
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getPaymentId()).isEqualTo(1L);
    }

    @Test
    void refund_failsWhenGuestTokenMissing() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(null)));

        assertThatThrownBy(() -> service.refund(
            null,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);

        verify(tossPaymentClient, never()).cancel(any());
    }

    @Test
    void refund_failsWhenEventAlreadyStarted() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(
                10L,
                "PAID",
                "PAID",
                "CONFIRMED",
                OffsetDateTime.now().minusMinutes(1)
            )));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_NOT_ALLOWED);

        verify(tossPaymentClient, never()).cancel(any());
    }

    @Test
    void refund_failsWhenExchangeCodeRedeemed() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L)));
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(
            2L,
            ExchangeCodeStatus.REDEEMED
        )).willReturn(true);

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.USED_TICKET_CANNOT_BE_REFUNDED);

        verify(tossPaymentClient, never()).cancel(any());
    }

    @Test
    void refund_returnsExistingCompletedRefundWithoutToss() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund refund = PaymentRefund.requested(
            1L,
            10L,
            BigDecimal.valueOf(10000),
            "reason",
            OffsetDateTime.now()
        );
        refund.complete("cancel-key", OffsetDateTime.now());
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.of(refund));

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(tossPaymentClient, never()).cancel(any());
    }

    private CreateRefundResponse refundResponse() {
        return new CreateRefundResponse(
            1L,
            1L,
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "COMPLETED",
            "reason",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
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

    private RefundPaymentProjection projection(Long buyerMemberId) {
        return projection(
            buyerMemberId,
            "PAID",
            "PAID",
            "CONFIRMED",
            OffsetDateTime.now().plusDays(1)
        );
    }

    private RefundPaymentProjection projection(
            Long buyerMemberId,
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime eventStartAt) {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return paymentStatus; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return buyerMemberId; }
            @Override public BigDecimal getTotalAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentOrderStatus() { return paymentOrderStatus; }
            @Override public Long getTicketOrderId() { return 2L; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event name"; }
            @Override public OffsetDateTime getEventStartAt() { return eventStartAt; }
            @Override public Integer getQuantity() { return 2; }
            @Override public String getTicketOrderStatus() { return ticketOrderStatus; }
        };
    }
}
