package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@Import({
    TestcontainersConfiguration.class,
    PaymentConfirmConcurrencyIntegrationTest.FakeTossPaymentClientConfig.class
})
@SpringBootTest(properties = {
    "payment.confirm-inflight-ttl=30s",
    "payment.finalization-lock-timeout-ms=5000",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class PaymentConfirmConcurrencyIntegrationTest {

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private FakeTossPaymentClient fakeTossPaymentClient;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        fakeTossPaymentClient.reset();
    }

    @Test
    void sameOrderConcurrentConfirmCallsTossConfirmOnceWhenRedisGateIsAvailable()
            throws Exception {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture order = createPendingOrder(memberId, eventId);
        String paymentKey = "payment-key-" + UUID.randomUUID();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
            paymentKey,
            order.orderNo(),
            BigDecimal.valueOf(10000)
        );

        int concurrency = 50;
        CountDownLatch start = new CountDownLatch(1);
        executorService = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(executorService.submit(() -> {
                await(start);
                try {
                    paymentConfirmService.confirm(memberId, null, request);
                } catch (RuntimeException ignored) {
                    // In-flight duplicates may receive 409 while the first request finalizes.
                }
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        assertThat(fakeTossPaymentClient.confirmInvocationCount()).isEqualTo(1);
        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertOrderStatus(order.paymentOrderId(), PaymentOrderStatus.PAID);
        assertTicketOrderStatus(order.paymentOrderId(), TicketOrderStatus.CONFIRMED);
        assertExchangeCodeCount(order.ticketOrderId(), 1);
    }

    @Test
    void confirmAndDoneWebhookConcurrentRaceCreatesOneFinalSideEffect()
            throws Exception {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture order = createPendingOrder(memberId, eventId);
        String paymentKey = "payment-key-" + UUID.randomUUID();
        ConfirmPaymentRequest confirmRequest = new ConfirmPaymentRequest(
            paymentKey,
            order.orderNo(),
            BigDecimal.valueOf(10000)
        );
        TossConfirmResponse tossPayment = new TossConfirmResponse(
            paymentKey,
            order.orderNo(),
            BigDecimal.valueOf(10000),
            "DONE",
            "CARD",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
        fakeTossPaymentClient.putPayment(paymentKey, tossPayment);

        CountDownLatch start = new CountDownLatch(1);
        executorService = Executors.newFixedThreadPool(2);
        Future<?> confirmFuture = executorService.submit(() -> {
            await(start);
            paymentConfirmService.confirm(memberId, null, confirmRequest);
        });
        Future<?> webhookFuture = executorService.submit(() -> {
            await(start);
            paymentWebhookService.handleTossWebhook(webhook(tossPayment));
        });

        start.countDown();
        confirmFuture.get(10, TimeUnit.SECONDS);
        webhookFuture.get(10, TimeUnit.SECONDS);

        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertOrderStatus(order.paymentOrderId(), PaymentOrderStatus.PAID);
        assertTicketOrderStatus(order.paymentOrderId(), TicketOrderStatus.CONFIRMED);
        assertExchangeCodeCount(order.ticketOrderId(), 1);
    }

    private TossPaymentWebhookRequest webhook(TossConfirmResponse tossPayment) {
        return new TossPaymentWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            "2026-08-21T10:00:00.000000",
            new TossPaymentWebhookRequest.PaymentData(
                tossPayment.paymentKey(),
                tossPayment.orderId(),
                tossPayment.totalAmount(),
                tossPayment.status(),
                tossPayment.method(),
                tossPayment.requestedAt(),
                tossPayment.approvedAt()
            )
        );
    }

    private OrderFixture createPendingOrder(Long memberId, Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(
            PaymentOrder.builder()
                .orderNo("ORDER-CONCURRENT-" + UUID.randomUUID())
                .buyerMemberId(memberId)
                .orderType(PaymentOrderType.EVENT_TICKET)
                .totalAmount(BigDecimal.valueOf(10000))
                .requestedPaymentMethod(PaymentMethod.CARD)
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
        return new OrderFixture(paymentOrder.getId(), ticketOrder.getId(), paymentOrder.getOrderNo());
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
            "confirm-" + suffix + "@example.com",
            "confirm-" + suffix,
            "confirm-subject-" + suffix
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
            "confirm-org-" + UUID.randomUUID(),
            "confirm-org@example.com"
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
            "confirm-event-" + UUID.randomUUID()
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
        PaymentOrder paymentOrder = paymentOrderRepository.findById(paymentOrderId).orElseThrow();
        assertThat(paymentOrder.getStatus()).isEqualTo(status.name());
    }

    private void assertTicketOrderStatus(Long paymentOrderId, TicketOrderStatus status) {
        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(paymentOrderId)
            .orElseThrow();
        assertThat(ticketOrder.getStatus()).isEqualTo(status.name());
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

    private record OrderFixture(Long paymentOrderId, Long ticketOrderId, String orderNo) {
    }

    static class FakeTossPaymentClient implements TossPaymentClient {

        private final AtomicInteger confirmInvocationCount = new AtomicInteger();
        private final Map<String, TossConfirmResponse> payments = new ConcurrentHashMap<>();

        @Override
        public TossConfirmResponse confirm(TossConfirmRequest request) {
            confirmInvocationCount.incrementAndGet();
            sleep(300L);
            TossConfirmResponse response = new TossConfirmResponse(
                request.paymentKey(),
                request.orderId(),
                BigDecimal.valueOf(request.amount()),
                "DONE",
                "CARD",
                OffsetDateTime.now(),
                OffsetDateTime.now()
            );
            payments.put(request.paymentKey(), response);
            return response;
        }

        @Override
        public TossConfirmResponse getPayment(String paymentKey) {
            return payments.get(paymentKey);
        }

        @Override
        public TossCancelResponse getPaymentForRefund(String paymentKey) {
            throw new UnsupportedOperationException("Not used in this test.");
        }

        @Override
        public TossCancelResponse cancel(TossCancelRequest request) {
            throw new UnsupportedOperationException("Not used in this test.");
        }

        int confirmInvocationCount() {
            return confirmInvocationCount.get();
        }

        void reset() {
            confirmInvocationCount.set(0);
            payments.clear();
        }

        void putPayment(String paymentKey, TossConfirmResponse response) {
            payments.put(paymentKey, response);
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    @TestConfiguration
    static class FakeTossPaymentClientConfig {

        @Bean
        @Primary
        FakeTossPaymentClient tossPaymentClient() {
            return new FakeTossPaymentClient();
        }
    }
}
