package com.min.edu.payment.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountExpirationProcessor {

    private static final String TOSS_DONE_STATUS = "DONE";
    private static final String TOSS_WAITING_FOR_DEPOSIT_STATUS = "WAITING_FOR_DEPOSIT";
    private static final String TOSS_EXPIRED_STATUS = "EXPIRED";
    private static final String TOSS_CANCELED_STATUS = "CANCELED";
    private static final String TOSS_ABORTED_STATUS = "ABORTED";

    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final VirtualAccountExpirationRepairService repairService;
    private final VirtualAccountReconciliationRepairService reconciliationRepairService;

    public void processPendingOrder(Long paymentOrderId, OffsetDateTime now) {
        PaymentOrder paymentOrder = paymentOrderRepository.findById(paymentOrderId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!paymentOrder.isPending()
                || paymentOrder.getExpiresAt() == null
                || paymentOrder.getExpiresAt().isAfter(now)) {
            return;
        }

        TossConfirmResponse tossPayment;
        try {
            tossPayment = tossPaymentClient.getPaymentByOrderId(paymentOrder.getOrderNo());
        } catch (TossPaymentClientException exception) {
            logPendingOrderProviderLookupSkipped(paymentOrder, exception.getErrorCode().name());
            return;
        } catch (RuntimeException exception) {
            logPendingOrderProviderLookupSkipped(
                paymentOrder,
                exception.getClass().getSimpleName()
            );
            return;
        }

        if (tossPayment == null || tossPayment.status() == null) {
            return;
        }
        if (TOSS_WAITING_FOR_DEPOSIT_STATUS.equals(tossPayment.status())
                || TOSS_DONE_STATUS.equals(tossPayment.status())) {
            reconciliationRepairService.repairPendingOrder(paymentOrder.getOrderNo(), tossPayment);
            return;
        }
        if (isProviderExpiredOrCanceled(tossPayment.status())) {
            repairService.expirePendingOrder(paymentOrderId, now, tossPayment.status());
        }
    }

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

        TossConfirmResponse tossPayment = getPayment(payment.getPaymentKey());
        repairService.process(virtualAccountId, now, tossPayment);
    }

    private TossConfirmResponse getPayment(String paymentKey) {
        try {
            return tossPaymentClient.getPayment(paymentKey);
        } catch (TossPaymentClientException exception) {
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private boolean isProviderExpiredOrCanceled(String status) {
        return TOSS_EXPIRED_STATUS.equals(status)
            || TOSS_CANCELED_STATUS.equals(status)
            || TOSS_ABORTED_STATUS.equals(status);
    }

    private void logPendingOrderProviderLookupSkipped(PaymentOrder paymentOrder, String reasonCode) {
        log.warn(
            "Pending virtual account expiration provider lookup skipped. paymentOrderId={}, reasonCode={}",
            paymentOrder.getId(),
            reasonCode
        );
    }
}
