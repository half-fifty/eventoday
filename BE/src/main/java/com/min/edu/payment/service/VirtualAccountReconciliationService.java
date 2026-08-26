package com.min.edu.payment.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.VirtualAccountReconciliationProperties;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountReconciliationService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final VirtualAccountReconciliationRepairService repairService;
    private final VirtualAccountReconciliationProperties properties;

    public void reconcilePendingVirtualAccountOrders() {
        if (!properties.isEnabled()) {
            return;
        }

        OffsetDateTime threshold = OffsetDateTime.now().minus(properties.getAgeThreshold());
        paymentOrderRepository.findSuspiciousPendingVirtualAccountOrders(
                threshold,
                PageRequest.of(0, properties.getBatchSize()))
            .forEach(this::reconcile);
    }

    private void reconcile(PaymentOrder candidate) {
        TossConfirmResponse providerPayment;
        try {
            providerPayment = tossPaymentClient.getPaymentByOrderId(candidate.getOrderNo());
        } catch (TossPaymentClientException exception) {
            logProviderLookupSkipped(candidate, exception.getErrorCode().name());
            return;
        } catch (RuntimeException exception) {
            logProviderLookupSkipped(candidate, exception.getClass().getSimpleName());
            return;
        }

        try {
            repairService.repairPendingOrder(candidate.getOrderNo(), providerPayment);
        } catch (RuntimeException exception) {
            log.warn(
                "Virtual account reconciliation repair failed. paymentOrderId={}, orderNo={}",
                candidate.getId(),
                candidate.getOrderNo(),
                exception
            );
        }
    }

    private void logProviderLookupSkipped(PaymentOrder candidate, String reasonCode) {
        if (GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID.name().equals(reasonCode)) {
            log.info(
                "Virtual account reconciliation provider lookup returned no usable payment. paymentOrderId={}, reasonCode={}",
                candidate.getId(),
                reasonCode
            );
            return;
        }

        log.warn(
            "Virtual account reconciliation provider lookup failed. paymentOrderId={}, reasonCode={}",
            candidate.getId(),
            reasonCode
        );
    }
}
