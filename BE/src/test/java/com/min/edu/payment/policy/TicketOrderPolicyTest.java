package com.min.edu.payment.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.event.EventTicketSnapshot;

class TicketOrderPolicyTest {

    private final TicketOrderPolicy ticketOrderPolicy = new TicketOrderPolicy();

    @Test
    void validate_failsWhenQuantityIsZero() {
        assertBusinessException(
            new CreateTicketOrderRequest(0, null),
            1L,
            GlobalErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    void validate_failsWhenQuantityIsNegative() {
        assertBusinessException(
            new CreateTicketOrderRequest(-1, null),
            1L,
            GlobalErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    void validate_failsWhenPurchaseLimitExceeded() {
        assertBusinessException(
            new CreateTicketOrderRequest(6, null),
            1L,
            GlobalErrorCode.TICKET_PURCHASE_LIMIT_EXCEEDED
        );
    }

    @Test
    void validate_failsBeforeSalesStart() {
        EventTicketSnapshot event = event(
            EventStatus.PUBLISHED,
            OffsetDateTime.now().plusMinutes(1),
            OffsetDateTime.now().plusHours(1),
            10
        );

        assertThatThrownBy(() -> ticketOrderPolicy.validate(
                1L,
                new CreateTicketOrderRequest(1, null),
                event,
                OffsetDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SALES_NOT_OPEN);
    }

    @Test
    void validate_failsAtOrAfterSalesEnd() {
        EventTicketSnapshot event = event(
            EventStatus.PUBLISHED,
            OffsetDateTime.now().minusHours(1),
            OffsetDateTime.now().minusSeconds(1),
            10
        );

        assertThatThrownBy(() -> ticketOrderPolicy.validate(
                1L,
                new CreateTicketOrderRequest(1, null),
                event,
                OffsetDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SALES_NOT_OPEN);
    }

    @Test
    void validate_failsWhenEventStatusIsNotPublished() {
        EventTicketSnapshot event = event(
            EventStatus.APPROVED,
            OffsetDateTime.now().minusHours(1),
            OffsetDateTime.now().plusHours(1),
            10
        );

        assertThatThrownBy(() -> ticketOrderPolicy.validate(
                1L,
                new CreateTicketOrderRequest(1, null),
                event,
                OffsetDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SALES_NOT_OPEN);
    }

    @Test
    void validate_failsWhenRemainingQuantityIsNotEnough() {
        assertBusinessException(
            new CreateTicketOrderRequest(2, null),
            1L,
            event(EventStatus.PUBLISHED, null, null, 1),
            GlobalErrorCode.TICKET_SOLD_OUT
        );
    }

    @Test
    void validate_failsWhenGuestBuyerIsMissing() {
        assertBusinessException(
            new CreateTicketOrderRequest(1, null),
            null,
            GlobalErrorCode.INVALID_GUEST_BUYER_INFO
        );
    }

    @Test
    void validate_failsWhenGuestBuyerNameIsMissing() {
        assertBusinessException(
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("", "guest@example.com", "010-1234-5678")),
            null,
            GlobalErrorCode.INVALID_GUEST_BUYER_INFO
        );
    }

    @Test
    void validate_failsWhenGuestBuyerEmailIsInvalid() {
        assertBusinessException(
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("guest", "invalid-email", "010-1234-5678")),
            null,
            GlobalErrorCode.INVALID_GUEST_BUYER_INFO
        );
    }

    @Test
    void validate_failsWhenGuestBuyerPhoneIsInvalid() {
        assertBusinessException(
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("guest", "guest@example.com", "010 abc")),
            null,
            GlobalErrorCode.INVALID_GUEST_BUYER_INFO
        );
    }

    @Test
    void validate_failsWhenGuestBuyerPhoneContainsOnlyHyphens() {
        assertBusinessException(
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("guest", "guest@example.com", "----")),
            null,
            GlobalErrorCode.INVALID_GUEST_BUYER_INFO
        );
    }

    @Test
    void validate_passesWhenGuestBuyerPhoneHasHyphens() {
        assertThatCode(() -> ticketOrderPolicy.validate(
                null,
                new CreateTicketOrderRequest(
                    1,
                    new GuestBuyerRequest("guest", "guest@example.com", "02-1234-5678")),
                event(EventStatus.PUBLISHED, null, null, 10),
                OffsetDateTime.now()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_passesWhenGuestBuyerPhoneHasOnlyDigits() {
        assertThatCode(() -> ticketOrderPolicy.validate(
                null,
                new CreateTicketOrderRequest(
                    1,
                    new GuestBuyerRequest("guest", "guest@example.com", "01012345678")),
                event(EventStatus.PUBLISHED, null, null, 10),
                OffsetDateTime.now()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_passesForMemberWithoutBuyer() {
        assertThatCode(() -> ticketOrderPolicy.validate(
                1L,
                new CreateTicketOrderRequest(1, null),
                event(EventStatus.PUBLISHED, null, null, 10),
                OffsetDateTime.now()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_passesInSalesPeriod() {
        EventTicketSnapshot event = event(
            EventStatus.PUBLISHED,
            OffsetDateTime.now().minusHours(1),
            OffsetDateTime.now().plusHours(1),
            10
        );

        assertThatCode(() -> ticketOrderPolicy.validate(
                null,
                new CreateTicketOrderRequest(
                    1,
                    new GuestBuyerRequest("guest", "guest@example.com", "010-1234-5678")),
                event,
                OffsetDateTime.now()))
            .doesNotThrowAnyException();
    }

    private void assertBusinessException(
            CreateTicketOrderRequest request,
            Long buyerMemberId,
            GlobalErrorCode expectedErrorCode) {
        assertBusinessException(
            request,
            buyerMemberId,
            event(EventStatus.PUBLISHED, null, null, 10),
            expectedErrorCode
        );
    }

    private void assertBusinessException(
            CreateTicketOrderRequest request,
            Long buyerMemberId,
            EventTicketSnapshot event,
            GlobalErrorCode expectedErrorCode) {
        assertThatThrownBy(() -> ticketOrderPolicy.validate(
                buyerMemberId,
                request,
                event,
                OffsetDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expectedErrorCode);
    }

    private EventTicketSnapshot event(
            EventStatus status,
            OffsetDateTime salesStartAt,
            OffsetDateTime salesEndAt,
            int remainingQuantity) {
        return new EventTicketSnapshot(
            1L,
            status,
            BigDecimal.valueOf(10000),
            10,
            10 - remainingQuantity,
            5,
            salesStartAt,
            salesEndAt
        );
    }
}
