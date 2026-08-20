package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestOrderAccessTokenRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketOrderCompletedResponseService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final GuestOrderAccessService guestOrderAccessService;

    public CreateTicketOrderResponse completedResponse(
            TicketOrderIdempotencyRequest idempotencyRequest,
            CreateTicketOrderRequest retryRequest) {
        PaymentOrder paymentOrder = paymentOrderRepository.findById(idempotencyRequest.getPaymentOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        TicketOrder ticketOrder = ticketOrderRepository.findById(idempotencyRequest.getTicketOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (ticketOrder.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
            List<ExchangeCode> exchangeCodes =
                exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId());
            return CreateTicketOrderResponse.free(
                paymentOrder,
                ticketOrder,
                exchangeCodes,
                issueGuestAccessTokenIfNeeded(paymentOrder, retryRequest)
            );
        }

        return CreateTicketOrderResponse.paymentPending(
            paymentOrder,
            ticketOrder,
            issueGuestAccessTokenIfNeeded(paymentOrder, retryRequest)
        );
    }

    private String issueGuestAccessTokenIfNeeded(
            PaymentOrder paymentOrder,
            CreateTicketOrderRequest retryRequest) {
        if (paymentOrder.getBuyerMemberId() != null) {
            return null;
        }

        if (retryRequest.getBuyer() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_GUEST_BUYER_INFO);
        }

        return guestOrderAccessService.issueGuestAccessToken(
            paymentOrder.getOrderNo(),
            new GuestOrderAccessTokenRequest(
                retryRequest.getBuyer().getEmail(),
                retryRequest.getBuyer().getPhone()
            )
        ).orderAccessToken();
    }
}
