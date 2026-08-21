package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.event.TicketReservationCompletedEvent;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
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
    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final TicketExchangeCodeIssuer ticketExchangeCodeIssuer;
    private final AdvertisementRepository advertisementRepository;
    private final EventRepository eventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PaymentAuditLogWriter auditLogWriter;

    @Transactional
    public ConfirmPaymentResponse finalizePayment(
            Long requesterMemberId,
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            requesterMemberId,
            request,
            tossResponse,
            properties.getFinalizationLockTimeoutMs(),
            PaymentAuditSource.CONFIRM
        );
    }

    @Transactional
    public ConfirmPaymentResponse finalizePaymentFromWebhook(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            null,
            request,
            tossResponse,
            properties.getWebhookFinalizationLockTimeoutMs(),
            PaymentAuditSource.WEBHOOK
        );
    }

    @Transactional
    public ConfirmPaymentResponse finalizePaymentFromReconciliation(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            null,
            request,
            tossResponse,
            properties.getWebhookFinalizationLockTimeoutMs(),
            PaymentAuditSource.RECONCILIATION
        );
    }

    @Transactional
    public ConfirmPaymentResponse finalizePaymentFromExpiration(
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        return finalizePayment(
            null,
            request,
            tossResponse,
            properties.getWebhookFinalizationLockTimeoutMs(),
            PaymentAuditSource.EXPIRATION
        );
    }

    private ConfirmPaymentResponse finalizePayment(
            Long requesterMemberId,
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse,
            long lockTimeoutMs,
            PaymentAuditSource source) {
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

        validateFinalizable(paymentOrder, ticketOrder, advertisement, request, tossResponse);

        Payment existingPayment = paymentRepository
            .findByPaymentOrderId(paymentOrder.getId())
            .orElse(null);

        OffsetDateTime now = OffsetDateTime.now();
        String fromStatus = paymentOrder.getStatus();
        Payment payment = completePayment(
            existingPayment,
            paymentOrder,
            request,
            tossResponse,
            now
        );

        markPaymentOrderPaid(paymentOrder, now);
        auditLogWriter.append(
            paymentOrder.getId(),
            payment.getId(),
            null,
            PaymentAuditEventType.PAYMENT_PAID,
            fromStatus,
            paymentOrder.getStatus(),
            source,
            null,
            actorType(source, requesterMemberId),
            actorId(source, requesterMemberId),
            null,
            now
        );
        if (ticketOrder != null) {
            ticketOrder.confirm(tossResponse.approvedAt(), now);
            ticketExchangeCodeIssuer.issueIfAbsent(ticketOrder, paymentOrder.getBuyerMemberId(), now);
            publishGuestReservationCompleted(paymentOrder, ticketOrder);
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
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse) {
        validateTossFinalizationResponse(request, paymentOrder, tossResponse);

        if (paymentOrder.getOrderType() != PaymentOrderType.EVENT_TICKET
                && paymentOrder.getOrderType() != PaymentOrderType.EVENT_AD) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (request.getAmount().compareTo(paymentOrder.getTotalAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        PaymentMethod requestedMethod = requestedMethod(paymentOrder);
        if (!requestedMethod.matchesTossMethod(tossResponse.method())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_METHOD_MISMATCH);
        }

        if (paymentOrder.getTotalAmount().signum() <= 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_NOT_REQUIRED);
        }

        boolean finalizablePaymentOrder = paymentOrder.isPending()
            || paymentOrder.isWaitingForDeposit();
        if (!finalizablePaymentOrder
                || (ticketOrder != null && !ticketOrder.isPendingPayment())
                || (advertisement != null && advertisement.getStatus() != AdvertisementStatus.PAYMENT_PENDING)
                || (advertisement == null && ticketOrder == null)) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }
    }

    private void validateTossFinalizationResponse(
            ConfirmPaymentRequest request,
            PaymentOrder paymentOrder,
            TossConfirmResponse tossResponse) {
        if (tossResponse == null
                || tossResponse.paymentKey() == null
                || tossResponse.orderId() == null
                || tossResponse.totalAmount() == null
                || tossResponse.requestedAt() == null
                || tossResponse.approvedAt() == null
                || !"DONE".equals(tossResponse.status())
                || !request.getPaymentKey().equals(tossResponse.paymentKey())
                || !request.getOrderId().equals(tossResponse.orderId())
                || request.getAmount().compareTo(tossResponse.totalAmount()) != 0
                || paymentOrder.getTotalAmount().compareTo(tossResponse.totalAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private Payment completePayment(
            Payment existingPayment,
            PaymentOrder paymentOrder,
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse,
            OffsetDateTime now) {
        if (requestedMethod(paymentOrder) == PaymentMethod.VIRTUAL_ACCOUNT) {
            return completeWaitingVirtualAccount(
                existingPayment,
                request,
                tossResponse,
                now
            );
        }

        if (existingPayment != null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        return paymentRepository.saveAndFlush(Payment.approved(
            paymentOrder.getId(),
            PaymentProvider.TOSS_PAYMENTS,
            request.getPaymentKey(),
            tossResponse.method(),
            request.getAmount(),
            tossResponse.requestedAt(),
            tossResponse.approvedAt(),
            now
        ));
    }

    private Payment completeWaitingVirtualAccount(
            Payment existingPayment,
            ConfirmPaymentRequest request,
            TossConfirmResponse tossResponse,
            OffsetDateTime now) {
        if (existingPayment == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        if (!request.getPaymentKey().equals(existingPayment.getPaymentKey())
                || request.getAmount().compareTo(existingPayment.getAmount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        if (existingPayment.isPaid()) {
            return existingPayment;
        }

        if (!existingPayment.isWaitingForDeposit()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        existingPayment.markPaid(
            tossResponse.method(),
            tossResponse.requestedAt(),
            tossResponse.approvedAt(),
            now
        );
        Payment payment = paymentRepository.saveAndFlush(existingPayment);
        PaymentVirtualAccount virtualAccount = virtualAccountRepository
            .findByPaymentId(payment.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        virtualAccount.markDeposited(tossResponse.status(), tossResponse.approvedAt(), now);
        return payment;
    }

    private void markPaymentOrderPaid(PaymentOrder paymentOrder, OffsetDateTime now) {
        if (paymentOrder.isPending()) {
            paymentOrder.markPaid(now);
            return;
        }

        if (paymentOrder.isWaitingForDeposit()) {
            paymentOrder.markPaidFromWaiting(now);
            return;
        }

        throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
    }

    private PaymentMethod requestedMethod(PaymentOrder paymentOrder) {
        return paymentOrder.getRequestedPaymentMethod() == null
            ? PaymentMethod.CARD
            : paymentOrder.getRequestedPaymentMethod();
    }

    private void setLocalLockTimeout(long lockTimeoutMs) {
        entityManager
            .createNativeQuery("select set_config('lock_timeout', :timeout, true)")
            .setParameter("timeout", lockTimeoutMs + "ms")
            .getSingleResult();
    }

    private PaymentAuditActorType actorType(PaymentAuditSource source, Long requesterMemberId) {
        return source == PaymentAuditSource.CONFIRM
            ? PaymentAuditActorType.fromRequester(requesterMemberId)
            : PaymentAuditActorType.SYSTEM;
    }

    private Long actorId(PaymentAuditSource source, Long requesterMemberId) {
        return source == PaymentAuditSource.CONFIRM ? requesterMemberId : null;
    }

    private void publishGuestReservationCompleted(
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder) {
        if (paymentOrder.getBuyerMemberId() != null
                || paymentOrder.getBuyerEmail() == null
                || paymentOrder.getBuyerEmail().isBlank()) {
            return;
        }

        String eventName = eventRepository.findById(ticketOrder.getEventId())
            .map(event -> event.getName())
            .orElse("");
        applicationEventPublisher.publishEvent(new TicketReservationCompletedEvent(
            paymentOrder.getOrderNo(),
            paymentOrder.getBuyerEmail(),
            eventName
        ));
    }
}
