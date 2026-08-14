package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.event.EventTicketReader;
import com.min.edu.payment.event.EventTicketSnapshot;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.event.TicketReservationCompletedEvent;
import com.min.edu.payment.policy.TicketOrderPolicy;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.support.OrderNoGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    private static final long PAYMENT_EXPIRATION_MINUTES = 10L;
    private static final long VIRTUAL_ACCOUNT_EXPIRATION_MINUTES = 30L;

    private final EventTicketReader eventTicketReader;
    private final TicketInventoryGateway ticketInventoryGateway;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final ExchangeCodeGenerator exchangeCodeGenerator;
    private final TicketOrderPolicy ticketOrderPolicy;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public CreateTicketOrderResponse create(
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        EventTicketSnapshot event = eventTicketReader.getTicketSnapshot(eventId);

        ticketOrderPolicy.validate(buyerMemberId, request, event, now);

        BigDecimal unitPrice = event.ticketPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        String orderNo = orderNoGenerator.generate();

        if (!ticketInventoryGateway.reserve(eventId, request.getQuantity())) {
            throw new BusinessException(GlobalErrorCode.TICKET_SOLD_OUT);
        }

        if (isFree(unitPrice)) {
            return createFreeOrder(
                eventId,
                buyerMemberId,
                request,
                orderNo,
                unitPrice,
                totalAmount,
                event,
                now
            );
        }

        return createPaymentPendingOrder(
            eventId,
            buyerMemberId,
            request,
            orderNo,
            unitPrice,
            totalAmount,
            event,
            now
        );
    }

    private CreateTicketOrderResponse createPaymentPendingOrder(
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request,
            String orderNo,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            EventTicketSnapshot event,
            OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.save(
            PaymentOrder.createTicketOrder(
                orderNo,
                buyerMemberId,
                getGuestBuyerName(buyerMemberId, request.getBuyer()),
                getGuestBuyerEmail(buyerMemberId, request.getBuyer()),
                getGuestBuyerPhone(buyerMemberId, request.getBuyer()),
                totalAmount,
                selectedPaymentMethod(request),
                PaymentOrderStatus.PENDING,
                paymentExpiresAt(selectedPaymentMethod(request), now),
                now
            )
        );

        TicketOrder ticketOrder = ticketOrderRepository.save(
            TicketOrder.create(
                paymentOrder.getId(),
                eventId,
                unitPrice,
                request.getQuantity(),
                TicketOrderStatus.PENDING_PAYMENT,
                null,
                now
            )
        );

        return CreateTicketOrderResponse.paymentPending(
            paymentOrder,
            ticketOrder,
            createOrderAccessTokenIfGuest(buyerMemberId, orderNo, event, now)
        );
    }

    private CreateTicketOrderResponse createFreeOrder(
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request,
            String orderNo,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            EventTicketSnapshot event,
            OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.save(
            PaymentOrder.createTicketOrder(
                orderNo,
                buyerMemberId,
                getGuestBuyerName(buyerMemberId, request.getBuyer()),
                getGuestBuyerEmail(buyerMemberId, request.getBuyer()),
                getGuestBuyerPhone(buyerMemberId, request.getBuyer()),
                totalAmount,
                PaymentMethod.CARD,
                PaymentOrderStatus.PAID,
                null,
                now
            )
        );

        TicketOrder ticketOrder = ticketOrderRepository.save(
            TicketOrder.create(
                paymentOrder.getId(),
                eventId,
                unitPrice,
                request.getQuantity(),
                TicketOrderStatus.CONFIRMED,
                now,
                now
            )
        );

        List<ExchangeCode> exchangeCodes = createExchangeCodes(
            eventId,
            buyerMemberId,
            ticketOrder.getId(),
            request.getQuantity(),
            now
        );
        publishGuestReservationCompleted(paymentOrder, event);

        return CreateTicketOrderResponse.free(
            paymentOrder,
            ticketOrder,
            exchangeCodes,
            createOrderAccessTokenIfGuest(buyerMemberId, orderNo, event, now)
        );
    }

    private String createOrderAccessTokenIfGuest(
            Long buyerMemberId,
            String orderNo,
            EventTicketSnapshot event,
            OffsetDateTime now) {
        if (buyerMemberId != null) {
            return null;
        }

        if (!event.endAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.TICKET_SALES_NOT_OPEN);
        }

        return orderAccessTokenProvider.create(orderNo, event.endAt());
    }

    private void publishGuestReservationCompleted(
            PaymentOrder paymentOrder,
            EventTicketSnapshot event) {
        if (paymentOrder.getBuyerMemberId() != null
                || paymentOrder.getBuyerEmail() == null
                || paymentOrder.getBuyerEmail().isBlank()) {
            return;
        }

        applicationEventPublisher.publishEvent(new TicketReservationCompletedEvent(
            paymentOrder.getOrderNo(),
            paymentOrder.getBuyerEmail(),
            event.eventName()
        ));
    }

    private List<ExchangeCode> createExchangeCodes(
            Long eventId,
            Long buyerMemberId,
            Long ticketOrderId,
            int quantity,
            OffsetDateTime now) {
        List<ExchangeCode> exchangeCodes = new ArrayList<>();

        for (int i = 0; i < quantity; i++) {
            ExchangeCode exchangeCode = ExchangeCode.createForTicketOrder(
                eventId,
                ticketOrderId,
                buyerMemberId,
                exchangeCodeGenerator.generate(),
                null,
                now
            );

            exchangeCodes.add(exchangeCodeRepository.save(exchangeCode));
        }

        return exchangeCodes;
    }

    private boolean isFree(BigDecimal unitPrice) {
        return unitPrice.compareTo(BigDecimal.ZERO) == 0;
    }

    private PaymentMethod selectedPaymentMethod(CreateTicketOrderRequest request) {
        return request.getPaymentMethod() == null
            ? PaymentMethod.CARD
            : request.getPaymentMethod();
    }

    private OffsetDateTime paymentExpiresAt(PaymentMethod paymentMethod, OffsetDateTime now) {
        if (paymentMethod == PaymentMethod.VIRTUAL_ACCOUNT) {
            return now.plusMinutes(VIRTUAL_ACCOUNT_EXPIRATION_MINUTES);
        }

        return now.plusMinutes(PAYMENT_EXPIRATION_MINUTES);
    }

    private String getGuestBuyerName(
            Long buyerMemberId,
            GuestBuyerRequest buyer) {
        return buyerMemberId == null ? buyer.getName() : null;
    }

    private String getGuestBuyerEmail(
            Long buyerMemberId,
            GuestBuyerRequest buyer) {
        return buyerMemberId == null ? buyer.getEmail() : null;
    }

    private String getGuestBuyerPhone(
            Long buyerMemberId,
            GuestBuyerRequest buyer) {
        return buyerMemberId == null ? buyer.getPhone() : null;
    }
}
