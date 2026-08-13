package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.MyTicketOrderListResponse;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.dto.response.TicketOrderListItemResponse;
import com.min.edu.payment.repository.TicketOrderDetailProjection;
import com.min.edu.payment.repository.TicketOrderListProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

@ExtendWith(MockitoExtension.class)
class TicketOrderQueryServiceTest {

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private OrderAccessTokenProvider orderAccessTokenProvider;

    @Test
    void getMyTicketOrders_returnsOnlyRepositoryResultForCurrentMember() {
        TicketOrderQueryService service = service();
        TicketOrderListProjection order = projection(
            2L,
            null,
            "EVT-20260803-000000000002",
            1L,
            "Event",
            1,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "PAID",
            "CONFIRMED",
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00")
        );
        given(ticketOrderRepository.findMyTicketOrders(10L, PageRequest.of(0, 20)))
            .willReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        MyTicketOrderListResponse response = service.getMyTicketOrders(10L, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        TicketOrderListItemResponse item = response.getContent().getFirst();
        assertThat(item.getTicketOrderId()).isEqualTo(2L);
        assertThat(item.getPaymentId()).isNull();
        assertThat(item.getOrderNo()).isEqualTo("EVT-20260803-000000000002");
        assertThat(item.getEventId()).isEqualTo(1L);
        assertThat(item.getEventName()).isEqualTo("Event");
        assertThat(item.getPaymentRequired()).isFalse();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isEmpty()).isFalse();
    }

    @Test
    void getMyTicketOrders_calculatesPaymentRequiredForPaidOrder() {
        TicketOrderQueryService service = service();
        TicketOrderListProjection order = projection(
            1L,
            5L,
            "EVT-20260803-000000000001",
            1L,
            "Event",
            2,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            "PENDING",
            "PENDING_PAYMENT",
            OffsetDateTime.parse("2026-08-03T10:10:00+09:00"),
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00")
        );
        given(ticketOrderRepository.findMyTicketOrders(10L, PageRequest.of(0, 20)))
            .willReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        MyTicketOrderListResponse response = service.getMyTicketOrders(10L, 0, 20);

        assertThat(response.getContent().getFirst().getPaymentRequired()).isTrue();
        assertThat(response.getContent().getFirst().getPaymentId()).isEqualTo(5L);
    }

    @Test
    void getMyTicketOrders_returnsEmptyPage() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findMyTicketOrders(10L, PageRequest.of(0, 20)))
            .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        MyTicketOrderListResponse response = service.getMyTicketOrders(10L, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isEmpty()).isTrue();
    }

    @Test
    void getMyTicketOrders_passesRequestedPageAndSize() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findMyTicketOrders(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 12));

        service.getMyTicketOrders(10L, 2, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketOrderRepository).findMyTicketOrders(
            org.mockito.ArgumentMatchers.eq(10L),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void getMyTicketOrders_failsWhenPrincipalIsMissing() {
        TicketOrderQueryService service = service();

        assertThatThrownBy(() -> service.getMyTicketOrders(null, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(ticketOrderRepository);
    }

    @Test
    void getMyTicketOrders_failsWhenPageIsNegative() {
        assertInvalidPageRequest(-1, 20);
    }

    @Test
    void getMyTicketOrders_failsWhenSizeIsZero() {
        assertInvalidPageRequest(0, 0);
    }

    @Test
    void getMyTicketOrders_failsWhenSizeIsGreaterThanMax() {
        assertInvalidPageRequest(0, 101);
    }

    private void assertInvalidPageRequest(int page, int size) {
        TicketOrderQueryService service = service();

        assertThatThrownBy(() -> service.getMyTicketOrders(10L, page, size))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(ticketOrderRepository);
    }

    @Test
    void getTicketOrderDetail_returnsMemberOrderForOwner() {
        TicketOrderQueryService service = service();
        TicketOrderDetailProjection order = detailProjection(1L, 5L, "ORDER-1", 10L);
        ExchangeCode exchangeCode = ExchangeCode.createForTicketOrder(
            100L,
            1L,
            10L,
            "A1B2-C3D4-E5F6",
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+09:00")
        );
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(order));
        given(exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(1L))
            .willReturn(List.of(exchangeCode));

        TicketOrderDetailResponse response =
            service.getTicketOrderDetail("ORDER-1", 10L, null);

        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(response.getTicketOrderId()).isEqualTo(1L);
        assertThat(response.getPaymentId()).isEqualTo(5L);
        assertThat(response.getPaymentRequired()).isTrue();
        assertThat(response.getExchangeCodes()).hasSize(1);
        assertThat(response.getExchangeCodes().getFirst().getCode())
            .isEqualTo("A1B2-C3D4-E5F6");
    }

    @Test
    void getTicketOrderDetail_returnsNullPaymentIdForFreeOrder() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("FREE-ORDER-1"))
            .willReturn(Optional.of(detailProjection(
                2L,
                null,
                "FREE-ORDER-1",
                10L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PAID",
                "CONFIRMED"
            )));
        given(exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(2L))
            .willReturn(List.of());

        TicketOrderDetailResponse response =
            service.getTicketOrderDetail("FREE-ORDER-1", 10L, null);

        assertThat(response.getOrderNo()).isEqualTo("FREE-ORDER-1");
        assertThat(response.getPaymentId()).isNull();
        assertThat(response.getPaymentRequired()).isFalse();
    }

    @Test
    void getTicketOrderDetail_failsWhenMemberOrderIsRequestedWithoutLogin() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", 10L)));

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", null, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_failsWhenMemberOrderIsRequestedByOtherMember() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", 10L)));

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", 20L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_returnsGuestOrderWithValidToken() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", null)));
        given(orderAccessTokenProvider.getOrderNo("guest-token")).willReturn("ORDER-1");
        given(exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(1L))
            .willReturn(List.of());

        TicketOrderDetailResponse response =
            service.getTicketOrderDetail("ORDER-1", null, "guest-token");

        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(response.getPaymentId()).isEqualTo(5L);
        assertThat(response.getExchangeCodes()).isEmpty();
    }

    @Test
    void getTicketOrderDetail_requiresTokenForGuestOrderEvenWhenLoggedIn() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", null)));

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", 10L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        verifyNoInteractions(orderAccessTokenProvider, exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_failsWhenGuestTokenOrderNoDoesNotMatch() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", null)));
        given(orderAccessTokenProvider.getOrderNo("guest-token")).willReturn("ORDER-2");

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", null, "guest-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_propagatesInvalidGuestToken() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", null)));
        given(orderAccessTokenProvider.getOrderNo("bad-token"))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID));

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", null, "bad-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_INVALID);
        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_propagatesExpiredGuestToken() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(detailProjection(1L, 5L, "ORDER-1", null)));
        given(orderAccessTokenProvider.getOrderNo("expired-token"))
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED));

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", null, "expired-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_EXPIRED);
        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getTicketOrderDetail_failsWhenOrderDoesNotExist() {
        TicketOrderQueryService service = service();
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicketOrderDetail("ORDER-1", 10L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
        verifyNoInteractions(exchangeCodeRepository);
    }

    private TicketOrderQueryService service() {
        return new TicketOrderQueryService(
            ticketOrderRepository,
            exchangeCodeRepository,
            orderAccessTokenProvider
        );
    }

    private TicketOrderListProjection projection(
            Long ticketOrderId,
            Long paymentId,
            String orderNo,
            Long eventId,
            String eventName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime expiresAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime createdAt) {
        return new TicketOrderListProjection() {
            @Override
            public Long getTicketOrderId() {
                return ticketOrderId;
            }

            @Override
            public Long getPaymentId() {
                return paymentId;
            }

            @Override
            public String getOrderNo() {
                return orderNo;
            }

            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return eventName;
            }

            @Override
            public Integer getQuantity() {
                return quantity;
            }

            @Override
            public BigDecimal getUnitPrice() {
                return unitPrice;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return totalAmount;
            }

            @Override
            public String getPaymentOrderStatus() {
                return paymentOrderStatus;
            }

            @Override
            public String getTicketOrderStatus() {
                return ticketOrderStatus;
            }

            @Override
            public String getPaymentMethod() {
                return "CARD";
            }

            @Override
            public String getVirtualAccountBankCode() {
                return null;
            }

            @Override
            public String getVirtualAccountNumber() {
                return null;
            }

            @Override
            public String getVirtualAccountCustomerName() {
                return null;
            }

            @Override
            public OffsetDateTime getVirtualAccountDueAt() {
                return null;
            }

            @Override
            public OffsetDateTime getExpiresAt() {
                return expiresAt;
            }

            @Override
            public OffsetDateTime getConfirmedAt() {
                return confirmedAt;
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }

    private TicketOrderDetailProjection detailProjection(
            Long ticketOrderId,
            Long paymentId,
            String orderNo,
            Long buyerMemberId) {
        return detailProjection(
            ticketOrderId,
            paymentId,
            orderNo,
            buyerMemberId,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(20000),
            "PENDING",
            "PENDING_PAYMENT"
        );
    }

    private TicketOrderDetailProjection detailProjection(
            Long ticketOrderId,
            Long paymentId,
            String orderNo,
            Long buyerMemberId,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String paymentOrderStatus,
            String ticketOrderStatus) {
        return new TicketOrderDetailProjection() {
            @Override
            public Long getTicketOrderId() {
                return ticketOrderId;
            }

            @Override
            public Long getPaymentId() {
                return paymentId;
            }

            @Override
            public String getOrderNo() {
                return orderNo;
            }

            @Override
            public Long getBuyerMemberId() {
                return buyerMemberId;
            }

            @Override
            public Long getEventId() {
                return 100L;
            }

            @Override
            public String getEventName() {
                return "Event";
            }

            @Override
            public Integer getQuantity() {
                return 2;
            }

            @Override
            public BigDecimal getUnitPrice() {
                return unitPrice;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return totalAmount;
            }

            @Override
            public String getPaymentOrderStatus() {
                return paymentOrderStatus;
            }

            @Override
            public String getTicketOrderStatus() {
                return ticketOrderStatus;
            }

            @Override
            public String getPaymentMethod() {
                return "CARD";
            }

            @Override
            public String getVirtualAccountBankCode() {
                return null;
            }

            @Override
            public String getVirtualAccountNumber() {
                return null;
            }

            @Override
            public String getVirtualAccountCustomerName() {
                return null;
            }

            @Override
            public OffsetDateTime getVirtualAccountDueAt() {
                return null;
            }

            @Override
            public OffsetDateTime getExpiresAt() {
                return OffsetDateTime.parse("2026-08-03T10:10:00+09:00");
            }

            @Override
            public OffsetDateTime getConfirmedAt() {
                return null;
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return OffsetDateTime.parse("2026-08-03T10:00:00+09:00");
            }
        };
    }
}
