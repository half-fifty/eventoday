package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.PaymentDetailResponse;
import com.min.edu.payment.repository.PaymentDetailProjection;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderAccessTokenProvider orderAccessTokenProvider;

    @Test
    void getPaymentDetail_succeedsForMemberOwner() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(10L)));

        PaymentDetailResponse response = service.getPaymentDetail(10L, null, 1L);

        assertThat(response.getPaymentId()).isEqualTo(1L);
        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(response.getTicketOrderId()).isEqualTo(2L);
        assertThat(response.getEventName()).isEqualTo("event name");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void getPaymentDetail_failsWhenMemberPrincipalMissing() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(10L)));

        assertBusinessException(
            () -> service.getPaymentDetail(null, null, 1L),
            GlobalErrorCode.UNAUTHORIZED
        );
    }

    @Test
    void getPaymentDetail_failsWhenDifferentMemberAccessesPayment() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(10L)));

        assertBusinessException(
            () -> service.getPaymentDetail(20L, null, 1L),
            GlobalErrorCode.PAYMENT_ACCESS_DENIED
        );
    }

    @Test
    void getPaymentDetail_succeedsForGuestWithToken() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");

        PaymentDetailResponse response = service.getPaymentDetail(null, "token", 1L);

        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
    }

    @Test
    void getPaymentDetail_failsWhenGuestTokenIsMissing() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));

        assertBusinessException(
            () -> service.getPaymentDetail(null, null, 1L),
            GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED
        );
    }

    @Test
    void getPaymentDetail_failsWhenAuthenticatedUserAccessesGuestPaymentWithoutToken() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));

        assertBusinessException(
            () -> service.getPaymentDetail(10L, null, 1L),
            GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED
        );
    }

    @Test
    void getPaymentDetail_failsWhenGuestTokenIsTampered() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("tampered-token"))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID));

        assertBusinessException(
            () -> service.getPaymentDetail(null, "tampered-token", 1L),
            GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID
        );
    }

    @Test
    void getPaymentDetail_failsWhenGuestTokenIsExpired() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("expired-token"))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED));

        assertBusinessException(
            () -> service.getPaymentDetail(null, "expired-token", 1L),
            GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED
        );
    }

    @Test
    void getPaymentDetail_failsWhenGuestTokenOrderNoDoesNotMatch() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("OTHER-ORDER");

        assertBusinessException(
            () -> service.getPaymentDetail(null, "token", 1L),
            GlobalErrorCode.PAYMENT_ACCESS_DENIED
        );
    }

    @Test
    void getPaymentDetail_failsWhenPaymentDoesNotExist() {
        PaymentQueryService service = service();
        given(paymentRepository.findPaymentDetailById(1L)).willReturn(Optional.empty());

        assertBusinessException(
            () -> service.getPaymentDetail(10L, null, 1L),
            GlobalErrorCode.PAYMENT_NOT_FOUND
        );
    }

    private PaymentQueryService service() {
        return new PaymentQueryService(paymentRepository, orderAccessTokenProvider);
    }

    private PaymentDetailProjection projection(Long buyerMemberId) {
        return new PaymentDetailProjection() {
            @Override
            public Long getPaymentId() {
                return 1L;
            }

            @Override
            public Long getPaymentOrderId() {
                return 11L;
            }

            @Override
            public String getOrderNo() {
                return "ORDER-1";
            }

            @Override
            public Long getBuyerMemberId() {
                return buyerMemberId;
            }

            @Override
            public Long getTicketOrderId() {
                return 2L;
            }

            @Override
            public Long getEventId() {
                return 3L;
            }

            @Override
            public String getEventName() {
                return "event name";
            }

            @Override
            public String getPgProvider() {
                return "TOSS_PAYMENTS";
            }

            @Override
            public String getMethod() {
                return "CARD";
            }

            @Override
            public BigDecimal getAmount() {
                return BigDecimal.valueOf(10000);
            }

            @Override
            public String getPaymentStatus() {
                return "PAID";
            }

            @Override
            public String getTicketOrderStatus() {
                return "CONFIRMED";
            }

            @Override
            public OffsetDateTime getRequestedAt() {
                return OffsetDateTime.parse("2026-08-03T10:00:00+09:00");
            }

            @Override
            public OffsetDateTime getApprovedAt() {
                return OffsetDateTime.parse("2026-08-03T10:01:00+09:00");
            }
        };
    }

    private void assertBusinessException(
            Runnable runnable,
            GlobalErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
