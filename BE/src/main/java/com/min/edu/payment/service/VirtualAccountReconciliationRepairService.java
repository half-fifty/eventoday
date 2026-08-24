package com.min.edu.payment.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.repository.AdvertisementRepository;
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
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.PaymentSecretHasher;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountReconciliationRepairService {

    private static final String TOSS_DONE_STATUS = "DONE";
    private static final String TOSS_WAITING_FOR_DEPOSIT_STATUS = "WAITING_FOR_DEPOSIT";

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final AdvertisementRepository advertisementRepository;
    private final PaymentFinalizer paymentFinalizer;
    private final PaymentAuditLogWriter auditLogWriter;

    @Transactional
    public void repairPendingOrder(String orderNo, TossConfirmResponse providerPayment) {
        PaymentOrder lockedOrder = paymentOrderRepository.findByOrderNoForUpdate(orderNo)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        if (!lockedOrder.isPending()) {
            return;
        }

        if (lockedOrder.getRequestedPaymentMethod() != PaymentMethod.VIRTUAL_ACCOUNT) {
            return;
        }

        Payment existingPayment = paymentRepository.findByPaymentOrderId(lockedOrder.getId())
            .orElse(null);
        if (existingPayment != null) {
            return;
        }

        validateRecoverableProviderPayment(lockedOrder, providerPayment);

        if (TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(providerPayment.status())) {
            createWaitingLocalState(lockedOrder, providerPayment);
            return;
        }

        if (TOSS_DONE_STATUS.equals(providerPayment.status())) {
            Payment payment = createWaitingLocalState(lockedOrder, providerPayment);
            paymentFinalizer.finalizePaymentFromReconciliation(
                new ConfirmPaymentRequest(
                    providerPayment.paymentKey(),
                    providerPayment.orderId(),
                    providerPayment.totalAmount()
                ),
                providerPayment
            );
            log.debug(
                "Virtual account reconciliation finalized recovered payment. paymentOrderId={}, paymentId={}",
                lockedOrder.getId(),
                payment.getId()
            );
            return;
        }

        auditLogWriter.append(
            lockedOrder.getId(),
            null,
            null,
            PaymentAuditEventType.VA_RECONCILIATION_INVESTIGATE,
            lockedOrder.getStatus(),
            lockedOrder.getStatus(),
            PaymentAuditSource.RECONCILIATION,
            "UNSUPPORTED_PROVIDER_STATUS_" + providerPayment.status(),
            PaymentAuditActorType.SYSTEM,
            null,
            null,
            OffsetDateTime.now()
        );
    }

    private Payment createWaitingLocalState(
            PaymentOrder lockedOrder,
            TossConfirmResponse providerPayment) {
        if (lockedOrder.getOrderType() == PaymentOrderType.EVENT_AD) {
            Advertisement advertisement = advertisementRepository
                    .findByPaymentOrderId(lockedOrder.getId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
            if (advertisement.getStatus() != AdvertisementStatus.PAYMENT_PENDING) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
            }
        } else {
            TicketOrder ticketOrder = ticketOrderRepository
                .findByPaymentOrderId(lockedOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
            if (lockedOrder.getOrderType() != PaymentOrderType.EVENT_TICKET
                    || !ticketOrder.isPendingPayment()) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String fromStatus = lockedOrder.getStatus();
        Payment payment = paymentRepository.saveAndFlush(Payment.waitingForDeposit(
            lockedOrder.getId(),
            PaymentProvider.TOSS_PAYMENTS,
            providerPayment.paymentKey(),
            providerPayment.method(),
            providerPayment.totalAmount(),
            providerPayment.requestedAt(),
            now
        ));
        lockedOrder.markWaitingForDeposit(now);
        virtualAccountRepository.save(PaymentVirtualAccount.create(
            payment.getId(),
            providerPayment.virtualAccount().bankCode(),
            providerPayment.virtualAccount().accountNumber(),
            providerPayment.virtualAccount().customerName(),
            providerPayment.virtualAccount().dueDate(),
            PaymentSecretHasher.sha256(providerPayment.secret()),
            providerPayment.status(),
            now
        ));
        auditLogWriter.append(
            lockedOrder.getId(),
            payment.getId(),
            null,
            PaymentAuditEventType.PAYMENT_WAITING_FOR_DEPOSIT,
            fromStatus,
            lockedOrder.getStatus(),
            PaymentAuditSource.RECONCILIATION,
            "VA_LOCAL_STATE_RECOVERED",
            PaymentAuditActorType.SYSTEM,
            null,
            null,
            now
        );
        return payment;
    }

    private void validateRecoverableProviderPayment(
            PaymentOrder lockedOrder,
            TossConfirmResponse providerPayment) {
        if (providerPayment == null
                || providerPayment.paymentKey() == null
                || providerPayment.paymentKey().isBlank()
                || providerPayment.orderId() == null
                || !providerPayment.orderId().equals(lockedOrder.getOrderNo())
                || providerPayment.totalAmount() == null
                || providerPayment.totalAmount().compareTo(lockedOrder.getTotalAmount()) != 0
                || providerPayment.requestedAt() == null
                || providerPayment.status() == null
                || providerPayment.status().isBlank()
                || providerPayment.method() == null
                || !PaymentMethod.VIRTUAL_ACCOUNT.matchesTossMethod(providerPayment.method())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }

        if (!TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(providerPayment.status())
                && !TOSS_DONE_STATUS.equals(providerPayment.status())) {
            return;
        }

        if (providerPayment.secret() == null
                || providerPayment.secret().isBlank()
                || providerPayment.virtualAccount() == null
                || providerPayment.virtualAccount().accountNumber() == null
                || providerPayment.virtualAccount().accountNumber().isBlank()
                || providerPayment.virtualAccount().bankCode() == null
                || providerPayment.virtualAccount().bankCode().isBlank()
                || providerPayment.virtualAccount().dueDate() == null) {
            throw new BusinessException(GlobalErrorCode.VIRTUAL_ACCOUNT_REQUIRED);
        }

        if (TOSS_DONE_STATUS.equals(providerPayment.status())
                && providerPayment.approvedAt() == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }
}
