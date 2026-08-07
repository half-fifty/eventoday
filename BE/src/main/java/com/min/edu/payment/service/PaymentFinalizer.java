package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.repository.AdvertisementRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentFinalizer {

    private final EntityManager entityManager;
    private final PaymentFinalizationProperties properties;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRepository paymentRepository;
    private final TicketExchangeCodeIssuer ticketExchangeCodeIssuer;
    private final AdvertisementRepository advertisementRepository;

    @Transactional
    public ConfirmPaymentResponse finalizePayment(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            request,
            tossResponse,
            properties.getFinalizationLockTimeoutMs()
        );
    }

    @Transactional
    public ConfirmPaymentResponse finalizePaymentFromWebhook(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            request,
            tossResponse,
            properties.getWebhookFinalizationLockTimeoutMs()
        );
    }

    private ConfirmPaymentResponse finalizePayment(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse,
            long lockTimeoutMs) {
        setLocalLockTimeout(lockTimeoutMs);

        PaymentOrder paymentOrder = paymentOrderRepository
            .findByOrderNoForUpdate(request.getOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        if (paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot(
                request.getPaymentKey(),
                paymentOrder.getId())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);
        }

        TicketOrder ticketOrder = paymentOrder.getOrderType() == PaymentOrderType.EVENT_TICKET
            ? ticketOrderRepository.findByPaymentOrderId(paymentOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT))
            : null;
        Advertisement advertisement = paymentOrder.getOrderType() == PaymentOrderType.EVENT_AD
            ? advertisementRepository.findByPaymentOrderId(paymentOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT))
            : null;
        if (ticketOrder == null && advertisement == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        if (paymentOrder.isPaid()) {
            return completedResponse(paymentOrder, ticketOrder, advertisement, request.getPaymentKey());
        }

        validateFinalizable(paymentOrder, ticketOrder, advertisement, request);

        Payment existingPayment = paymentRepository
            .findByPaymentOrderId(paymentOrder.getId())
            .orElse(null);
        if (existingPayment != null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = paymentRepository.saveAndFlush(Payment.approved(
            paymentOrder.getId(),
            PaymentProvider.TOSS_PAYMENTS,
            request.getPaymentKey(),
            tossResponse.method(),
            request.getAmount(),
            tossResponse.requestedAt(),
            tossResponse.approvedAt(),
            now
        ));

        paymentOrder.markPaid(now);
        if (ticketOrder != null) {
            ticketOrder.confirm(tossResponse.approvedAt(), now);
            ticketExchangeCodeIssuer.issueIfAbsent(ticketOrder, paymentOrder.getBuyerMemberId(), now);
            return ConfirmPaymentResponse.of(payment, paymentOrder.getOrderNo(), ticketOrder);
        }
        advertisement.markPaid(now);
        return ConfirmPaymentResponse.ofEventAd(payment, paymentOrder.getOrderNo(), advertisement);
    }

    private ConfirmPaymentResponse completedResponse(
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder,
            Advertisement advertisement,
            String paymentKey) {
        Payment payment = paymentRepository
            .findByPaymentOrderId(paymentOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (!paymentKey.equals(payment.getPaymentKey())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        if (ticketOrder != null) {
            ticketExchangeCodeIssuer.issueIfAbsent(ticketOrder, paymentOrder.getBuyerMemberId(), OffsetDateTime.now());
            return ConfirmPaymentResponse.of(payment, paymentOrder.getOrderNo(), ticketOrder);
        }
        return ConfirmPaymentResponse.ofEventAd(payment, paymentOrder.getOrderNo(), advertisement);
    }

    private void validateFinalizable(
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder,
            Advertisement advertisement,
            ConfirmPaymentRequest request) {
        if (paymentOrder.getOrderType() != PaymentOrderType.EVENT_TICKET
                && paymentOrder.getOrderType() != PaymentOrderType.EVENT_AD) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (request.getAmount().compareTo(paymentOrder.getTotalAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (paymentOrder.getTotalAmount().signum() <= 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_NOT_REQUIRED);
        }

        if (!paymentOrder.isPending()
                || (ticketOrder != null && !ticketOrder.isPendingPayment())
                || (advertisement != null && advertisement.getStatus() != AdvertisementStatus.PAYMENT_PENDING)
                || (advertisement == null && ticketOrder == null)) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }
    }

    private void setLocalLockTimeout(long lockTimeoutMs) {
        entityManager
            .createNativeQuery("select set_config('lock_timeout', :timeout, true)")
            .setParameter("timeout", lockTimeoutMs + "ms")
            .getSingleResult();
    }
}
