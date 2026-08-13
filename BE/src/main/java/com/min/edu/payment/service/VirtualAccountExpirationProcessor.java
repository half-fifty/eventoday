package com.min.edu.payment.service;

import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VirtualAccountExpirationProcessor {

    private static final String TOSS_DONE_STATUS = "DONE";

    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final TicketInventoryGateway ticketInventoryGateway;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentFinalizer paymentFinalizer;

    @Transactional
    public void processPendingOrder(Long paymentOrderId, OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.findById(paymentOrderId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        PaymentOrder lockedOrder = paymentOrderRepository.findByOrderNoForUpdate(paymentOrder.getOrderNo())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!lockedOrder.isPending()
                || lockedOrder.getExpiresAt() == null
                || lockedOrder.getExpiresAt().isAfter(now)
                || paymentRepository.findByPaymentOrderId(lockedOrder.getId()).isPresent()) {
            return;
        }

        TicketOrder ticketOrder = ticketOrderRepository.findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        if (!ticketOrder.isPendingPayment()) {
            return;
        }

        if (!exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId()).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        if (!ticketInventoryGateway.release(
                ticketOrder.getEventId(),
                ticketOrder.getTotalQuantity())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        lockedOrder.expire(now);
        ticketOrder.expire(now);
    }

    @Transactional
    public void process(Long virtualAccountId, OffsetDateTime now) {
        PaymentVirtualAccount virtualAccount = virtualAccountRepository.findById(virtualAccountId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        if (virtualAccount.getDueAt().isAfter(now)) {
            return;
        }

        Payment payment = paymentRepository.findById(virtualAccount.getPaymentId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isWaitingForDeposit()) {
            return;
        }

        PaymentOrder paymentOrder = paymentOrderRepository.findById(payment.getPaymentOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        PaymentOrder lockedOrder = paymentOrderRepository.findByOrderNoForUpdate(paymentOrder.getOrderNo())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        TicketOrder ticketOrder = ticketOrderRepository.findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (!lockedOrder.isWaitingForDeposit()
                || !ticketOrder.isPendingPayment()
                || !payment.isWaitingForDeposit()) {
            return;
        }

        TossConfirmResponse tossPayment = getPayment(payment.getPaymentKey());
        if (TOSS_DONE_STATUS.equals(tossPayment.status())) {
            paymentFinalizer.finalizePaymentFromWebhook(
                new ConfirmPaymentRequest(
                    tossPayment.paymentKey(),
                    tossPayment.orderId(),
                    tossPayment.totalAmount()
                ),
                tossPayment
            );
            return;
        }

        if (!exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId()).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        if (!ticketInventoryGateway.release(
                ticketOrder.getEventId(),
                ticketOrder.getTotalQuantity())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        lockedOrder.expire(now);
        payment.expire(now);
        ticketOrder.expire(now);
        virtualAccount.markExpired(now);
    }

    private TossConfirmResponse getPayment(String paymentKey) {
        try {
            return tossPaymentClient.getPayment(paymentKey);
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }
}
