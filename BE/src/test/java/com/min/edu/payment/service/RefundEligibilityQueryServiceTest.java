package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.PaymentRefundStatus;
import com.min.edu.payment.policy.RefundEligibilityPolicy;
import com.min.edu.payment.policy.RefundEligibilityReasonCode;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefundEligibilityQueryServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentRefundRepository paymentRefundRepository;
    private ExchangeCodeRepository exchangeCodeRepository;
    private OrderAccessTokenProvider orderAccessTokenProvider;
    private RefundEligibilityQueryService service;

    @BeforeEach
    void setUp() {
        paymentRepository = org.mockito.Mockito.mock(PaymentRepository.class);
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        exchangeCodeRepository = org.mockito.Mockito.mock(ExchangeCodeRepository.class);
        orderAccessTokenProvider = org.mockito.Mockito.mock(OrderAccessTokenProvider.class);
        service = new RefundEligibilityQueryService(
            paymentRepository,
            paymentRefundRepository,
            exchangeCodeRepository,
            orderAccessTokenProvider,
            new RefundEligibilityPolicy(new EventOperationDeadlinePolicy())
        );
    }

    @Test
    void evaluateAllowsMemberOwnerAndBuildsEligibilityView() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L)));
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(2L, ExchangeCodeStatus.REDEEMED))
            .willReturn(true);
        PaymentRefund refund = PaymentRefund.builder()
            .status(PaymentRefundStatus.REQUESTED)
            .build();
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.of(refund));

        RefundEligibilityView view = service.evaluate(10L, null, 1L);

        assertThat(view.paymentId()).isEqualTo(1L);
        assertThat(view.refundStatus()).isEqualTo("REQUESTED");
        assertThat(view.eligibility().reasonCode())
            .isEqualTo(RefundEligibilityReasonCode.EXCHANGE_CODE_ALREADY_REDEEMED);
        assertThat(view.eligibility().exchangeCodeRedeemed()).isTrue();
        verify(exchangeCodeRepository).existsByTicketOrderIdAndStatus(2L, ExchangeCodeStatus.REDEEMED);
    }

    @Test
    void evaluateAllowsGuestWithMatchingOrderAccessToken() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");

        RefundEligibilityView view = service.evaluate(null, "token", 1L);

        assertThat(view.eventName()).isEqualTo("event");
    }

    @Test
    void evaluateRejectsOtherMemberBeforePolicy() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L)));

        assertThatThrownBy(() -> service.evaluate(20L, null, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);
    }

    @Test
    void evaluateRejectsMissingOrMismatchedGuestToken() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(null)));

        assertThatThrownBy(() -> service.evaluate(null, null, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);

        given(orderAccessTokenProvider.getOrderNo("other")).willReturn("OTHER");
        assertThatThrownBy(() -> service.evaluate(null, "other", 1L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);
    }

    private RefundPaymentProjection projection(Long buyerMemberId) {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return "PAID"; }
            @Override public String getPaymentMethod() { return "CARD"; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return buyerMemberId; }
            @Override public BigDecimal getTotalAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentOrderStatus() { return "PAID"; }
            @Override public Long getTicketOrderId() { return 2L; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event"; }
            @Override public OffsetDateTime getEventStartAt() { return OffsetDateTime.now(); }
            @Override public OffsetDateTime getEventEndAt() { return OffsetDateTime.now().plusHours(3); }
            @Override public Integer getQuantity() { return 2; }
            @Override public String getTicketOrderStatus() { return "CONFIRMED"; }
        };
    }
}
