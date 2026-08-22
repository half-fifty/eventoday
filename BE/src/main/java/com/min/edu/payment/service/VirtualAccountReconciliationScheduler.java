package com.min.edu.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "payment.virtual-account-reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountReconciliationScheduler {

    private final VirtualAccountReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "#{@virtualAccountReconciliationProperties.fixedDelay.toMillis()}")
    public void reconcilePendingVirtualAccountOrders() {
        try {
            reconciliationService.reconcilePendingVirtualAccountOrders();
        } catch (RuntimeException exception) {
            log.warn("Virtual account reconciliation scheduler failed.", exception);
        }
    }
}
