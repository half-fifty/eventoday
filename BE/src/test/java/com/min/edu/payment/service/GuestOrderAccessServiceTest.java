package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.GuestOrderAccessTokenRequest;
import com.min.edu.payment.dto.response.GuestOrderAccessTokenResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

@ExtendWith(MockitoExtension.class)
class GuestOrderAccessServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private EventRepository eventRepository;
    @Mock private OrderAccessTokenProvider orderAccessTokenProvider;

    @Test
    void issueGuestAccessToken_succeedsWhenOrderEmailAndPhoneMatch() {
        GuestOrderAccessService service = service();
        OffsetDateTime eventEndAt = OffsetDateTime.now().plusDays(1);
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(guestPaymentOrder("ORDER-1")));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder()));
        given(eventRepository.findById(100L)).willReturn(Optional.of(event(eventEndAt)));
        given(orderAccessTokenProvider.create("ORDER-1", eventEndAt)).willReturn("new-token");

        GuestOrderAccessTokenResponse response = service.issueGuestAccessToken(
            "ORDER-1",
            new GuestOrderAccessTokenRequest(" GUEST@example.com ", "010-1234-5678")
        );

        assertThat(response.orderNo()).isEqualTo("ORDER-1");
        assertThat(response.orderAccessToken()).isEqualTo("new-token");
        assertThat(response.expiresAt()).isEqualTo(eventEndAt);
    }

    @Test
    void issueGuestAccessToken_failsWithSameErrorForMissingOrder() {
        GuestOrderAccessService service = service();
        given(paymentOrderRepository.findByOrderNo("NOPE")).willReturn(Optional.empty());

        assertDenied(() -> service.issueGuestAccessToken(
            "NOPE",
            new GuestOrderAccessTokenRequest("guest@example.com", "010-1234-5678")
        ));
        verifyNoInteractions(ticketOrderRepository, eventRepository, orderAccessTokenProvider);
    }

    @Test
    void issueGuestAccessToken_failsBeforeTicketOrEventLookupWhenEmailOrPhoneIsWrong() {
        GuestOrderAccessService service = service();
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(guestPaymentOrder("ORDER-1")));

        assertDenied(() -> service.issueGuestAccessToken(
            "ORDER-1",
            new GuestOrderAccessTokenRequest("other@example.com", "010-1234-5678")
        ));
        assertDenied(() -> service.issueGuestAccessToken(
            "ORDER-1",
            new GuestOrderAccessTokenRequest("guest@example.com", "010-0000-0000")
        ));
        verifyNoInteractions(ticketOrderRepository, eventRepository, orderAccessTokenProvider);
    }

    @Test
    void issueGuestAccessToken_failsBeforeTicketOrEventLookupForMemberOrder() {
        GuestOrderAccessService service = service();
        given(paymentOrderRepository.findByOrderNo("MEMBER-ORDER"))
            .willReturn(Optional.of(memberPaymentOrder("MEMBER-ORDER")));

        assertDenied(() -> service.issueGuestAccessToken(
            "MEMBER-ORDER",
            new GuestOrderAccessTokenRequest("guest@example.com", "010-1234-5678")
        ));
        verify(ticketOrderRepository, never()).findByPaymentOrderId(2L);
        verifyNoInteractions(eventRepository, orderAccessTokenProvider);
    }

    @Test
    void validateGuestTicketOrderAccess_allowsOnlyMatchingGuestOrderToken() {
        GuestOrderAccessService service = service();
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(guestPaymentOrder("ORDER-1")));
        given(orderAccessTokenProvider.getOrderNo("guest-token")).willReturn("ORDER-1");
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder()));

        GuestTicketOrderAccess access =
            service.validateGuestTicketOrderAccess("ORDER-1", "guest-token");

        assertThat(access.ticketOrderId()).isEqualTo(10L);
        assertThat(access.eventId()).isEqualTo(100L);
    }

    @Test
    void validateGuestTicketOrderAccess_rejectsMissingDifferentOrMemberOrderToken() {
        GuestOrderAccessService service = service();
        assertThatThrownBy(() -> service.validateGuestTicketOrderAccess("ORDER-1", null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        verifyNoInteractions(paymentOrderRepository);

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(guestPaymentOrder("ORDER-1")));
        given(orderAccessTokenProvider.getOrderNo("other-token")).willReturn("ORDER-2");
        assertThatThrownBy(() -> service.validateGuestTicketOrderAccess("ORDER-1", "other-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);

        given(paymentOrderRepository.findByOrderNo("MEMBER-ORDER"))
            .willReturn(Optional.of(memberPaymentOrder("MEMBER-ORDER")));
        assertThatThrownBy(() -> service.validateGuestTicketOrderAccess("MEMBER-ORDER", "guest-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);
    }

    private void assertDenied(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
    }

    private GuestOrderAccessService service() {
        return new GuestOrderAccessService(
            paymentOrderRepository,
            ticketOrderRepository,
            eventRepository,
            orderAccessTokenProvider
        );
    }

    private PaymentOrder guestPaymentOrder(String orderNo) {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo(orderNo)
            .buyerName("Guest")
            .buyerEmail("guest@example.com")
            .buyerPhone("010-1234-5678")
            .orderType(com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PAID.name())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOrder memberPaymentOrder(String orderNo) {
        return PaymentOrder.builder()
            .id(2L)
            .orderNo(orderNo)
            .buyerMemberId(99L)
            .orderType(com.min.edu.payment.domain.PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PAID.name())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private TicketOrder ticketOrder() {
        return TicketOrder.builder()
            .id(10L)
            .paymentOrderId(1L)
            .eventId(100L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(TicketOrderStatus.CONFIRMED.name())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Event event(OffsetDateTime endAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return Event.builder()
            .id(100L)
            .organizerOrganizationId(1L)
            .name("Event")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("venue")
            .address("address")
            .startAt(now)
            .endAt(endAt)
            .ticketPrice(BigDecimal.valueOf(10000))
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(1)
            .ticketPurchaseLimit(5)
            .status(EventStatus.PUBLISHED)
            .boothRecruitmentEnabled(false)
            .venueMapEnabled(false)
            .boothReservationEnabled(false)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
