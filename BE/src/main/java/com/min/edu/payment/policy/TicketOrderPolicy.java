package com.min.edu.payment.policy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.event.EventTicketSnapshot;

@Component
public class TicketOrderPolicy {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^(?:\\d{2,3}-?\\d{3,4}-?\\d{4})$");

    public void validate(
            Long buyerMemberId,
            CreateTicketOrderRequest request,
            EventTicketSnapshot event,
            OffsetDateTime now) {
        validateBuyer(buyerMemberId, request.getBuyer());
        validateEvent(event, now);
        validateQuantity(request.getQuantity(), event);
    }

    private void validateBuyer(
            Long buyerMemberId,
            GuestBuyerRequest buyer) {
        if (buyerMemberId != null) {
            return;
        }

        if (buyer == null
                || isBlank(buyer.getName())
                || isBlank(buyer.getEmail())
                || isBlank(buyer.getPhone())
                || !EMAIL_PATTERN.matcher(buyer.getEmail()).matches()
                || !PHONE_PATTERN.matcher(buyer.getPhone()).matches()) {
            throw new BusinessException(GlobalErrorCode.INVALID_GUEST_BUYER_INFO);
        }
    }

    private void validateEvent(
            EventTicketSnapshot event,
            OffsetDateTime now) {
        if (event.status() != EventStatus.PUBLISHED
                || event.ticketPrice() == null
                || event.ticketPrice().compareTo(BigDecimal.ZERO) < 0
                || isBeforeSalesStart(event, now)
                || isAfterSalesEnd(event, now)) {
            throw new BusinessException(GlobalErrorCode.TICKET_SALES_NOT_OPEN);
        }
    }

    private void validateQuantity(
            Integer quantity,
            EventTicketSnapshot event) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        if (event.ticketPurchaseLimit() == null
                || quantity > event.ticketPurchaseLimit()) {
            throw new BusinessException(GlobalErrorCode.TICKET_PURCHASE_LIMIT_EXCEEDED);
        }

        int remainingQuantity =
            event.ticketTotalQuantity() - event.ticketSoldQuantity();

        if (quantity > remainingQuantity) {
            throw new BusinessException(GlobalErrorCode.TICKET_SOLD_OUT);
        }
    }

    private boolean isBeforeSalesStart(
            EventTicketSnapshot event,
            OffsetDateTime now) {
        return event.ticketSalesStartAt() != null
            && now.isBefore(event.ticketSalesStartAt());
    }

    private boolean isAfterSalesEnd(
            EventTicketSnapshot event,
            OffsetDateTime now) {
        return event.ticketSalesEndAt() != null
            && !now.isBefore(event.ticketSalesEndAt());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
