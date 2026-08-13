package com.min.edu.payment.service;

import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountExpirationScheduler {

    private static final int BATCH_SIZE = 50;

    private final PaymentVirtualAccountRepository virtualAccountRepository;
    private final com.min.edu.payment.repository.PaymentOrderRepository paymentOrderRepository;
    private final VirtualAccountExpirationProcessor processor;

    @Scheduled(fixedDelayString = "${payment.virtual-account-expiration-fixed-delay-ms:60000}")
    public void expireDueVirtualAccounts() {
        OffsetDateTime now = OffsetDateTime.now();
        virtualAccountRepository.findDueWaitingAccounts(now, PageRequest.of(0, BATCH_SIZE))
            .forEach(virtualAccount -> {
                try {
                    processor.process(virtualAccount.getId(), now);
                } catch (RuntimeException exception) {
                    log.warn(
                        "Virtual account expiration failed. virtualAccountId={}, paymentId={}",
                        virtualAccount.getId(),
                        virtualAccount.getPaymentId(),
                        exception
                    );
                }
            });

        paymentOrderRepository.findExpiredPendingVirtualAccountOrders(now, PageRequest.of(0, BATCH_SIZE))
            .forEach(paymentOrder -> {
                try {
                    processor.processPendingOrder(paymentOrder.getId(), now);
                } catch (RuntimeException exception) {
                    log.warn(
                        "Pending virtual account order expiration failed. paymentOrderId={}, orderNo={}",
                        paymentOrder.getId(),
                        paymentOrder.getOrderNo(),
                        exception
                    );
                }
            });
    }
}
