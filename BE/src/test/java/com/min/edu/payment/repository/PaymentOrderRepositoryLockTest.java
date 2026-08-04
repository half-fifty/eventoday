package com.min.edu.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "payment.finalization-lock-timeout-ms=300")
class PaymentOrderRepositoryLockTest {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void findByOrderNoForUpdate_failsWhenPostgresLockTimeoutExpires() throws Exception {
        String orderNo = "ORDER-" + UUID.randomUUID();
        paymentOrderRepository.saveAndFlush(paymentOrder(orderNo));

        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionFinished = new CountDownLatch(1);
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        executorService = Executors.newFixedThreadPool(2);
        executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentOrderRepository.findByOrderNoForUpdate(orderNo).orElseThrow();
            firstTransactionLocked.countDown();
            await(releaseFirstTransaction);
        }));

        assertThat(firstTransactionLocked.await(5, TimeUnit.SECONDS)).isTrue();

        executorService.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    entityManager
                        .createNativeQuery("select set_config('lock_timeout', '300ms', true)")
                        .getSingleResult();
                    paymentOrderRepository.findByOrderNoForUpdate(orderNo);
                });
            } catch (Throwable throwable) {
                secondFailure.set(throwable);
            } finally {
                secondTransactionFinished.countDown();
            }
        });

        assertThat(secondTransactionFinished.await(5, TimeUnit.SECONDS)).isTrue();
        releaseFirstTransaction.countDown();

        assertThat(secondFailure.get())
            .isInstanceOf(PessimisticLockingFailureException.class);
    }

    private PaymentOrder paymentOrder(String orderNo) {
        OffsetDateTime now = OffsetDateTime.now();
        return PaymentOrder.builder()
            .orderNo(orderNo)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(now.plusMinutes(10))
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
