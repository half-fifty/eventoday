package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.event.EventTicketReader;
import com.min.edu.payment.event.EventTicketSnapshot;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.policy.TicketOrderPolicy;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderNoGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    private static final long PAYMENT_EXPIRATION_MINUTES = 10L;

    private final EventTicketReader eventTicketReader;
    private final TicketInventoryGateway ticketInventoryGateway;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final ExchangeCodeGenerator exchangeCodeGenerator;
    private final TicketOrderPolicy ticketOrderPolicy;

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
            OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.save(
            PaymentOrder.createTicketOrder(
                orderNo,
                buyerMemberId,
                getGuestBuyerName(buyerMemberId, request.getBuyer()),
                getGuestBuyerEmail(buyerMemberId, request.getBuyer()),
                getGuestBuyerPhone(buyerMemberId, request.getBuyer()),
                totalAmount,
                PaymentOrderStatus.PENDING,
                now.plusMinutes(PAYMENT_EXPIRATION_MINUTES),
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

        return CreateTicketOrderResponse.paymentPending(paymentOrder, ticketOrder);
    }

    private CreateTicketOrderResponse createFreeOrder(
            Long eventId,
            Long buyerMemberId,
            CreateTicketOrderRequest request,
            String orderNo,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.save(
            PaymentOrder.createTicketOrder(
                orderNo,
                buyerMemberId,
                getGuestBuyerName(buyerMemberId, request.getBuyer()),
                getGuestBuyerEmail(buyerMemberId, request.getBuyer()),
                getGuestBuyerPhone(buyerMemberId, request.getBuyer()),
                totalAmount,
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

        return CreateTicketOrderResponse.free(
            paymentOrder,
            ticketOrder,
            exchangeCodes
        );
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
