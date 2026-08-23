package com.min.edu.payment.service;

import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VirtualAccountExpirationRepairService {

    private static final String TOSS_DONE_STATUS = "DONE";

    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final TicketInventoryGateway ticketInventoryGateway;
    private final PaymentFinalizer paymentFinalizer;
    private final PaymentAuditLogWriter auditLogWriter;

    @Transactional
    public void expirePendingOrder(Long paymentOrderId, OffsetDateTime now, String providerStatus) {
        PaymentOrder lockedOrder = paymentOrderRepository.findByIdForUpdate(paymentOrderId)
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
            return;
        }

        if (!ticketInventoryGateway.release(
                ticketOrder.getEventId(),
                ticketOrder.getTotalQuantity())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        String fromStatus = lockedOrder.getStatus();
        lockedOrder.expire(now);
        ticketOrder.expire(now);
        auditLogWriter.append(
            lockedOrder.getId(),
            null,
            null,
            PaymentAuditEventType.PAYMENT_EXPIRED,
            fromStatus,
            lockedOrder.getStatus(),
            PaymentAuditSource.EXPIRATION,
            "PROVIDER_" + providerStatus,
            PaymentAuditActorType.SYSTEM,
            null,
            null,
            now
        );
    }

    @Transactional
    public void process(Long virtualAccountId, OffsetDateTime now, TossConfirmResponse tossPayment) {
        PaymentVirtualAccount virtualAccount = virtualAccountRepository.findById(virtualAccountId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        if (virtualAccount.getDueAt().isAfter(now)) {
            return;
        }

        PaymentOrder lockedOrder = paymentOrderRepository.findByVirtualAccountIdForUpdate(virtualAccountId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        Payment payment = paymentRepository.findByPaymentOrderIdForUpdate(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));
        if (!virtualAccount.getPaymentId().equals(payment.getId())
                || !payment.isWaitingForDeposit()) {
            return;
        }

        TicketOrder ticketOrder = ticketOrderRepository.findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (!lockedOrder.isWaitingForDeposit()
                || !ticketOrder.isPendingPayment()
                || !payment.isWaitingForDeposit()) {
            return;
        }

        if (TOSS_DONE_STATUS.equals(tossPayment.status())) {
            paymentFinalizer.finalizePaymentFromExpiration(
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
            return;
        }

        if (!ticketInventoryGateway.release(
                ticketOrder.getEventId(),
                ticketOrder.getTotalQuantity())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        String fromStatus = lockedOrder.getStatus();
        lockedOrder.expire(now);
        payment.expire(now);
        ticketOrder.expire(now);
        virtualAccount.markExpired(now);
        auditLogWriter.append(
            lockedOrder.getId(),
            payment.getId(),
            null,
            PaymentAuditEventType.PAYMENT_EXPIRED,
            fromStatus,
            lockedOrder.getStatus(),
            PaymentAuditSource.EXPIRATION,
            null,
            PaymentAuditActorType.SYSTEM,
            null,
            null,
            now
        );
    }
}
