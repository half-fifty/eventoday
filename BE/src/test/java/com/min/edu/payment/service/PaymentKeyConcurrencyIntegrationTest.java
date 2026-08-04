package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@Import({
    TestcontainersConfiguration.class,
    PaymentKeyConcurrencyIntegrationTest.FakeTossPaymentClientConfig.class
})
@SpringBootTest(properties = "payment.finalization-lock-timeout-ms=5000")
class PaymentKeyConcurrencyIntegrationTest {

    private static final String PAYMENT_KEY_UNIQUE_CONSTRAINT =
        "payments_payment_key_key";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void confirm_convertsConcurrentDuplicatePaymentKeyToConflict() throws Exception {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture first = createPendingOrder(memberId, eventId, "ORDER-A");
        OrderFixture second = createPendingOrder(memberId, eventId, "ORDER-B");
        String paymentKey = "payment-key-" + UUID.randomUUID();

        CountDownLatch firstFinalizedButNotCommitted = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        executorService = Executors.newFixedThreadPool(2);

        Future<?> firstFuture = executorService.submit(() ->
            transactionTemplate.executeWithoutResult(status -> {
                paymentConfirmService.confirm(
                    memberId,
                    null,
                    request(paymentKey, first.orderNo())
                );
                firstFinalizedButNotCommitted.countDown();
                await(releaseFirstTransaction);
            })
        );

        assertThat(firstFinalizedButNotCommitted.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> secondFuture = executorService.submit(() -> {
            try {
                paymentConfirmService.confirm(
                    memberId,
                    null,
                    request(paymentKey, second.orderNo())
                );
            } catch (Throwable throwable) {
                secondFailure.set(throwable);
            }
        });

        Thread.sleep(1000L);
        releaseFirstTransaction.countDown();

        firstFuture.get(5, TimeUnit.SECONDS);
        secondFuture.get(5, TimeUnit.SECONDS);

        assertThat(secondFailure.get())
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_KEY_ALREADY_USED);

        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertOrderStatus(first.paymentOrderId(), PaymentOrderStatus.PAID);
        assertTicketOrderStatus(first.paymentOrderId(), TicketOrderStatus.CONFIRMED);
        assertExchangeCodeCount(first.ticketOrderId(), 1);
        assertOrderStatus(second.paymentOrderId(), PaymentOrderStatus.PENDING);
        assertTicketOrderStatus(second.paymentOrderId(), TicketOrderStatus.PENDING_PAYMENT);
        assertExchangeCodeCount(second.ticketOrderId(), 0);
    }

    @Test
    void paymentKeyUniqueViolationExposesExpectedConstraintName() {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture first = createPendingOrder(memberId, eventId, "ORDER-C");
        OrderFixture second = createPendingOrder(memberId, eventId, "ORDER-D");
        String paymentKey = "payment-key-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        paymentRepository.saveAndFlush(Payment.approved(
            first.paymentOrderId(),
            PaymentProvider.TOSS_PAYMENTS,
            paymentKey,
            "CARD",
            BigDecimal.valueOf(10000),
            now,
            now,
            now
        ));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(Payment.approved(
            second.paymentOrderId(),
            PaymentProvider.TOSS_PAYMENTS,
            paymentKey,
            "CARD",
            BigDecimal.valueOf(10000),
            now,
            now,
            now
        )))
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(throwable -> {
                ConstraintViolationException constraintViolation =
                    findCause(throwable, ConstraintViolationException.class);
                assertThat(constraintViolation).isNotNull();
                assertThat(constraintViolation.getConstraintName())
                    .isEqualTo(PAYMENT_KEY_UNIQUE_CONSTRAINT);
                assertThat(constraintViolation.getSQLState())
                    .isEqualTo(UNIQUE_VIOLATION_SQL_STATE);
            });
    }

    private ConfirmPaymentRequest request(String paymentKey, String orderNo) {
        return new ConfirmPaymentRequest(
            paymentKey,
            orderNo,
            BigDecimal.valueOf(10000)
        );
    }

    private OrderFixture createPendingOrder(
            Long memberId,
            Long eventId,
            String orderNoPrefix) {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(
            PaymentOrder.builder()
                .orderNo(orderNoPrefix + "-" + UUID.randomUUID())
                .buyerMemberId(memberId)
                .orderType(PaymentOrderType.EVENT_TICKET)
                .totalAmount(BigDecimal.valueOf(10000))
                .status(PaymentOrderStatus.PENDING.name())
                .expiresAt(now.plusMinutes(10))
                .createdAt(now)
                .updatedAt(now)
                .build()
        );
        TicketOrder ticketOrder = ticketOrderRepository.saveAndFlush(
            TicketOrder.builder()
                .paymentOrderId(paymentOrder.getId())
                .eventId(eventId)
                .unitPrice(BigDecimal.valueOf(10000))
                .totalQuantity(1)
                .status(TicketOrderStatus.PENDING_PAYMENT.name())
                .createdAt(now)
                .updatedAt(now)
                .build()
        );

        return new OrderFixture(
            paymentOrder.getId(),
            ticketOrder.getId(),
            paymentOrder.getOrderNo()
        );
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID().toString();
        return insert("""
            INSERT INTO members (
                email,
                nickname,
                oauth_provider,
                oauth_subject,
                platform_role,
                status,
                created_at,
                updated_at
            ) VALUES (?, ?, 'GOOGLE', ?, 'USER', 'ACTIVE', now(), now())
            """,
            "member-" + suffix + "@example.com",
            "member-" + suffix,
            "subject-" + suffix
        );
    }

    private Long insertEvent() {
        Long organizationId = insert("""
            INSERT INTO organizations (
                organization_type,
                name,
                contact_email,
                contact_phone,
                status,
                created_at,
                updated_at
            ) VALUES ('COMPANY', ?, ?, '010-1234-5678', 'ACTIVE', now(), now())
            """,
            "org-" + UUID.randomUUID(),
            "org@example.com"
        );

        return insert("""
            INSERT INTO events (
                organizer_organization_id,
                name,
                event_type,
                description,
                venue_name,
                address,
                start_at,
                end_at,
                ticket_price,
                ticket_total_quantity,
                ticket_sold_quantity,
                ticket_purchase_limit,
                status,
                booth_recruitment_enabled,
                venue_map_enabled,
                booth_reservation_enabled,
                no_show_grace_minutes,
                created_at,
                updated_at
            ) VALUES (?, ?, 'EXPO', 'description', 'venue', 'address',
                now(), now() + interval '1 day', 10000, 100, 0, 5,
                'PUBLISHED', false, false, false, 0, now(), now())
            """,
            organizationId,
            "event-" + UUID.randomUUID()
        );
    }

    private Long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                sql,
                new String[] {"id"}
            );
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void assertOrderStatus(Long paymentOrderId, PaymentOrderStatus status) {
        PaymentOrder paymentOrder = paymentOrderRepository
            .findById(paymentOrderId)
            .orElseThrow();
        assertThat(paymentOrder.getStatus()).isEqualTo(status.name());
    }

    private void assertTicketOrderStatus(
            Long paymentOrderId,
            TicketOrderStatus status) {
        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(paymentOrderId)
            .orElseThrow();
        assertThat(ticketOrder.getStatus()).isEqualTo(status.name());
        if (status == TicketOrderStatus.CONFIRMED) {
            assertThat(ticketOrder.getConfirmedAt()).isNotNull();
        } else {
            assertThat(ticketOrder.getConfirmedAt()).isNull();
        }
    }

    private void assertExchangeCodeCount(Long ticketOrderId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM exchange_codes WHERE ticket_order_id = ?",
            Integer.class,
            ticketOrderId
        );
        assertThat(count).isEqualTo(expectedCount);
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

    private <T extends Throwable> T findCause(
            Throwable throwable,
            Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private record OrderFixture(
            Long paymentOrderId,
            Long ticketOrderId,
            String orderNo
    ) {
    }

    @TestConfiguration
    static class FakeTossPaymentClientConfig {

        @Bean
        @Primary
        TossPaymentClient tossPaymentClient() {
            return new TossPaymentClient() {
                @Override
                public TossConfirmResponse confirm(TossConfirmRequest request) {
                    return new TossConfirmResponse(
                        request.paymentKey(),
                        request.orderId(),
                        BigDecimal.valueOf(request.amount()),
                        "DONE",
                        "CARD",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                    );
                }

                @Override
                public TossConfirmResponse getPayment(String paymentKey) {
                    throw new UnsupportedOperationException("Not used in this test.");
                }
            };
        }
    }
}
