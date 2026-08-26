package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.MyRefundListResponse;
import com.min.edu.payment.dto.response.RefundDetailResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.RefundDetailProjection;
import com.min.edu.payment.repository.RefundListProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;

class RefundQueryServiceTest {

    private PaymentRefundRepository paymentRefundRepository;
    private OrderAccessTokenProvider orderAccessTokenProvider;
    private RefundQueryService service;

    @BeforeEach
    void setUp() {
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        orderAccessTokenProvider = org.mockito.Mockito.mock(OrderAccessTokenProvider.class);
        service = new RefundQueryService(paymentRefundRepository, orderAccessTokenProvider);
    }

    @Test
    void getMyRefunds_succeedsForMember() {
        given(paymentRefundRepository.findMyRefunds(10L, PageRequest.of(0, 20)))
            .willReturn(new PageImpl<>(
                java.util.List.of(listProjection()),
                PageRequest.of(0, 20),
                1
            ));

        MyRefundListResponse response = service.getMyRefunds(10L, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRefundStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getMyRefunds_failsWhenPrincipalMissing() {
        assertThatThrownBy(() -> service.getMyRefunds(null, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
    }

    @Test
    void getRefundDetail_succeedsForGuestWithToken() {
        given(paymentRefundRepository.findRefundDetailById(1L))
            .willReturn(Optional.of(detailProjection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");

        RefundDetailResponse response = service.getRefundDetail(null, "token", 1L);

        assertThat(response.getRefundId()).isEqualTo(1L);
        assertThat(response.getPaymentMethod()).isEqualTo("CARD");
    }

    @Test
    void getRefundDetail_failsForDifferentMember() {
        given(paymentRefundRepository.findRefundDetailById(1L))
            .willReturn(Optional.of(detailProjection(10L)));

        assertThatThrownBy(() -> service.getRefundDetail(20L, null, 1L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);
    }

    @Test
    void getRefundDetail_failsWhenGuestTokenOrderNoDoesNotMatch() {
        given(paymentRefundRepository.findRefundDetailById(1L))
            .willReturn(Optional.of(detailProjection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("OTHER-ORDER");

        assertThatThrownBy(() -> service.getRefundDetail(null, "token", 1L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);
    }

    private RefundListProjection listProjection() {
        return new RefundListProjection() {
            @Override public Long getRefundId() { return 1L; }
            @Override public Long getPaymentId() { return 2L; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event name"; }
            @Override public BigDecimal getRefundAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getRefundReason() { return "reason"; }
            @Override public String getRefundStatus() { return "COMPLETED"; }
            @Override public OffsetDateTime getRequestedAt() { return OffsetDateTime.now(); }
            @Override public OffsetDateTime getCompletedAt() { return OffsetDateTime.now(); }
        };
    }

    private RefundDetailProjection detailProjection(Long buyerMemberId) {
        return new RefundDetailProjection() {
            @Override public Long getRefundId() { return 1L; }
            @Override public Long getPaymentId() { return 2L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return buyerMemberId; }
            @Override public Long getTicketOrderId() { return 3L; }
            @Override public Long getEventId() { return 4L; }
            @Override public String getEventName() { return "event name"; }
            @Override public BigDecimal getRefundAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getRefundReason() { return "reason"; }
            @Override public String getRefundStatus() { return "COMPLETED"; }
            @Override public OffsetDateTime getRequestedAt() { return OffsetDateTime.now(); }
            @Override public OffsetDateTime getCompletedAt() { return OffsetDateTime.now(); }
            @Override public String getPaymentMethod() { return "CARD"; }
            @Override public String getTicketOrderStatus() { return "REFUNDED"; }
        };
    }
}
