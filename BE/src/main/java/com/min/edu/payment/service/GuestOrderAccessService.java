package com.min.edu.payment.service;

import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.GuestOrderAccessTokenRequest;
import com.min.edu.payment.dto.response.GuestOrderAccessTokenResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuestOrderAccessService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final EventRepository eventRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;

    @Transactional(readOnly = true)
    public GuestOrderAccessTokenResponse issueGuestAccessToken(
            String orderNo,
            GuestOrderAccessTokenRequest request) {
        PaymentOrder paymentOrder = findPaymentOrder(orderNo);
        validateGuestRecoverable(paymentOrder, request);

        TicketOrder ticketOrder = findTicketOrder(paymentOrder);
        Event event = findEvent(ticketOrder.getEventId());

        String token = orderAccessTokenProvider.create(orderNo, event.getEndAt());
        return new GuestOrderAccessTokenResponse(orderNo, token, event.getEndAt());
    }

    @Transactional(readOnly = true)
    public GuestTicketOrderAccess validateGuestTicketOrderAccess(
            String orderNo,
            String orderAccessToken) {
        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        PaymentOrder paymentOrder = findPaymentOrder(orderNo);
        if (paymentOrder.getBuyerMemberId() != null
                || paymentOrder.getOrderType() != PaymentOrderType.EVENT_TICKET) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!orderNo.equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }

        TicketOrder ticketOrder = findTicketOrder(paymentOrder);
        return new GuestTicketOrderAccess(
            ticketOrder.getId(),
            ticketOrder.getEventId(),
            paymentOrder.getOrderNo()
        );
    }

    private PaymentOrder findPaymentOrder(String orderNo) {
        return paymentOrderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND));
    }

    private TicketOrder findTicketOrder(PaymentOrder paymentOrder) {
        if (paymentOrder.getOrderType() != PaymentOrderType.EVENT_TICKET) {
            throw new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        return ticketOrderRepository.findByPaymentOrderId(paymentOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND));
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private void validateGuestRecoverable(
            PaymentOrder paymentOrder,
            GuestOrderAccessTokenRequest request) {
        if (paymentOrder.getBuyerMemberId() != null) {
            throw new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
        }

        String expectedEmail = normalizeEmail(paymentOrder.getBuyerEmail());
        String requestedEmail = normalizeEmail(request.getEmail());
        String expectedPhone = normalizePhone(paymentOrder.getBuyerPhone());
        String requestedPhone = normalizePhone(request.getPhone());

        if (expectedEmail.isBlank()
                || expectedPhone.isBlank()
                || !expectedEmail.equals(requestedEmail)
                || !expectedPhone.equals(requestedPhone)) {
            throw new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
        }
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.trim();
    }
}
