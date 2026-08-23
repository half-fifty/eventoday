package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.domain.TicketOrderIdempotencyStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "ticket-order.reliability.admission.max-in-flight-per-event=200"
})
class TicketOrderServiceIdempotencyIntegrationTest {

    @Autowired
    private TicketOrderService ticketOrderService;

    @Autowired
    private TicketOrderIdempotencyRequestRepository idempotencyRequestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void sameKeyConcurrentRequestsCreateOnlyOneSideEffect() throws Exception {
        Long eventId = createEvent(100);
        String idempotencyKey = "same-key-" + UUID.randomUUID();
        CreateTicketOrderRequest request = guestRequest();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger inProgress = new AtomicInteger();
        AtomicReference<Throwable> unexpectedFailure = new AtomicReference<>();

        executorService = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                await(start);
                try {
                    ticketOrderService.create(idempotencyKey, eventId, null, request);
                    success.incrementAndGet();
                } catch (BusinessException exception) {
                    if (exception.getErrorCode() == GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS) {
                        inProgress.incrementAndGet();
                    } else {
                        unexpectedFailure.compareAndSet(null, exception);
                    }
                } catch (Throwable throwable) {
                    unexpectedFailure.compareAndSet(null, throwable);
                }
            });
        }

        start.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpectedFailure.get()).isNull();
        assertThat(success.get()).isEqualTo(1);
        assertThat(inProgress.get()).isEqualTo(99);
        assertThat(ticketOrderCount(eventId)).isEqualTo(1);
        assertThat(idempotencyRequestRepository.findByIdempotencyKey(idempotencyKey))
            .hasValueSatisfying(requestRow ->
                assertThat(requestRow.getStatus()).isEqualTo(TicketOrderIdempotencyStatus.COMPLETED));
    }

    @Test
    void admittedSuccessCommitsCompletedIdempotencyRequest() {
        Long eventId = createEvent(1);
        String idempotencyKey = "success-" + UUID.randomUUID();

        ticketOrderService.create(idempotencyKey, eventId, null, guestRequest());

        assertThat(ticketOrderCount(eventId)).isEqualTo(1);
        assertThat(idempotencyRequestRepository.findByIdempotencyKey(idempotencyKey))
            .hasValueSatisfying(requestRow ->
                assertThat(requestRow.getStatus()).isEqualTo(TicketOrderIdempotencyStatus.COMPLETED));
    }

    @Test
    void admittedBusinessFailureRollsBackSideEffectsAndCommitsFailedIdempotencyRequest() {
        Long eventId = createEvent(0);
        String idempotencyKey = "failure-" + UUID.randomUUID();

        try {
            ticketOrderService.create(idempotencyKey, eventId, null, guestRequest());
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.TICKET_SOLD_OUT);
        }

        assertThat(ticketOrderCount(eventId)).isZero();
        assertThat(idempotencyRequestRepository.findByIdempotencyKey(idempotencyKey))
            .hasValueSatisfying(requestRow ->
                assertThat(requestRow.getStatus()).isEqualTo(TicketOrderIdempotencyStatus.FAILED));
    }

    private CreateTicketOrderRequest guestRequest() {
        return new CreateTicketOrderRequest(
            1,
            new GuestBuyerRequest("guest", "guest@example.com", "01012345678")
        );
    }

    private Long createEvent(int totalQuantity) {
        Long organizationId = jdbcTemplate.queryForObject("""
            insert into organizations (
                organization_type, name, business_number, representative_name,
                contact_email, contact_phone, status, created_at, updated_at
            )
            values ('COMPANY', 'Ticket Order Org', ?, 'Tester',
                    'ticket-order-org@example.local', '01012345678', 'ACTIVE', now(), now())
            returning id
            """, Long.class, "TO-" + UUID.randomUUID().toString().substring(0, 12));

        return jdbcTemplate.queryForObject("""
            insert into events (
                organizer_organization_id, name, event_type, short_description, description,
                venue_name, address, contact_email, contact_phone, postal_code, address_detail,
                region_code, start_at, end_at, ticket_sales_start_at, ticket_sales_end_at,
                ticket_price, ticket_total_quantity, ticket_sold_quantity, ticket_purchase_limit,
                status, booth_recruitment_enabled, venue_map_enabled, booth_reservation_enabled,
                no_show_grace_minutes, published_at, created_at, updated_at
            )
            values (?, 'Ticket Order Event', 'EXPO', 'test', 'test',
                    'Test Venue', 'Seoul', 'event@example.local', '01012345678', '00000', 'Hall',
                    'SEOUL', now() + interval '1 day', now() + interval '2 days',
                    now() - interval '1 day', now() + interval '1 day',
                    ?, ?, 0, 1,
                    'PUBLISHED', false, false, false, 0, now(), now(), now())
            returning id
            """, Long.class, organizationId, BigDecimal.valueOf(10000), totalQuantity);
    }

    private int ticketOrderCount(Long eventId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from ticket_orders where event_id = ?",
            Integer.class,
            eventId
        );
        return count == null ? 0 : count;
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
