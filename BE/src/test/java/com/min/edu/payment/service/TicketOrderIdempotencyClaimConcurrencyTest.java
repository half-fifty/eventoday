package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;
import com.min.edu.payment.repository.TicketOrderRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "ticket-order.reliability.idempotency-processing-ttl=100ms"
})
class TicketOrderIdempotencyClaimConcurrencyTest {

    @Autowired
    private TicketOrderIdempotencyClaimService claimService;

    @Autowired
    private TicketOrderIdempotencyRequestRepository repository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

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
    void duplicateClaimDoesNotEnterBusinessCreationWhileFirstExpiredProcessingIsStillRunning()
            throws Exception {
        Long eventId = createEvent();
        String idempotencyKey = "expiry-race-key-" + UUID.randomUUID();
        String requestHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        AtomicInteger businessCreationEntries = new AtomicInteger();
        AtomicReference<TicketOrderIdempotencyClaimResult> secondClaim = new AtomicReference<>();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch allowFirstComplete = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);

        executorService = Executors.newFixedThreadPool(2);
        Future<?> first = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            TicketOrderIdempotencyClaimResult claim = claimService.claim(idempotencyKey, requestHash, eventId);
            assertThat(claim.status()).isEqualTo(TicketOrderIdempotencyClaimStatus.CLAIMED);

            businessCreationEntries.incrementAndGet();
            PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(paymentOrder());
            TicketOrder ticketOrder = ticketOrderRepository.saveAndFlush(ticketOrder(paymentOrder.getId(), eventId));
            firstClaimed.countDown();
            await(allowFirstComplete);
            claim.request().complete(paymentOrder.getId(), ticketOrder.getId(), OffsetDateTime.now());
        }));

        assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(250);

        Future<?> second = executorService.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    secondClaim.set(claimService.claim(idempotencyKey, requestHash, eventId))
                );
            } finally {
                secondFinished.countDown();
            }
        });

        assertThat(secondFinished.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(businessCreationEntries.get()).isEqualTo(1);

        allowFirstComplete.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertThat(secondClaim.get().status()).isEqualTo(TicketOrderIdempotencyClaimStatus.COMPLETED);
        assertThat(businessCreationEntries.get()).isEqualTo(1);
        Integer ticketOrderCount = jdbcTemplate.queryForObject(
            "select count(*) from ticket_orders where event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(ticketOrderCount).isEqualTo(1);
        assertThat(repository.findByIdempotencyKey(idempotencyKey)).hasValueSatisfying(request -> {
            assertThat(request.getTicketOrderId()).isNotNull();
            assertThat(request.isCompleted()).isTrue();
        });
    }

    private Long createEvent() {
        Long organizationId = jdbcTemplate.queryForObject("""
            insert into organizations (
                organization_type, name, business_number, representative_name,
                contact_email, contact_phone, status, created_at, updated_at
            )
            values ('COMPANY', 'Expiry Race Org', ?, 'Tester',
                    'expiry-race-org@example.local', '01012345678', 'ACTIVE', now(), now())
            returning id
            """, Long.class, "ER-" + UUID.randomUUID().toString().substring(0, 12));

        return jdbcTemplate.queryForObject("""
            insert into events (
                organizer_organization_id, name, event_type, short_description, description,
                venue_name, address, contact_email, contact_phone, postal_code, address_detail,
                region_code, start_at, end_at, ticket_sales_start_at, ticket_sales_end_at,
                ticket_price, ticket_total_quantity, ticket_sold_quantity, ticket_purchase_limit,
                status, booth_recruitment_enabled, venue_map_enabled, booth_reservation_enabled,
                no_show_grace_minutes, published_at, created_at, updated_at
            )
            values (?, 'Expiry Race Event', 'EXPO', 'test', 'test',
                    'Test Venue', 'Seoul', 'event@example.local', '01012345678', '00000', 'Hall',
                    'SEOUL', now() + interval '1 day', now() + interval '2 days',
                    now() - interval '1 day', now() + interval '1 day',
                    10000, 100, 0, 1,
                    'PUBLISHED', false, false, false, 0, now(), now(), now())
            returning id
            """, Long.class, organizationId);
    }

    private PaymentOrder paymentOrder() {
        OffsetDateTime now = OffsetDateTime.now();
        return PaymentOrder.createTicketOrder(
            "ORDER-" + UUID.randomUUID(),
            null,
            "guest",
            "guest@example.com",
            "01012345678",
            BigDecimal.valueOf(10000),
            PaymentMethod.CARD,
            PaymentOrderStatus.PENDING,
            now.plusMinutes(10),
            now
        );
    }

    private TicketOrder ticketOrder(Long paymentOrderId, Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        return TicketOrder.create(
            paymentOrderId,
            eventId,
            BigDecimal.valueOf(10000),
            1,
            TicketOrderStatus.PENDING_PAYMENT,
            null,
            now
        );
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
