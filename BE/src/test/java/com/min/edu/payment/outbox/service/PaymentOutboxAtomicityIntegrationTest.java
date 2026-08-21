package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventType;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.service.PaymentFinalizer;
import com.min.edu.payment.service.TicketOrderCreationProcessor;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "spring.flyway.postgresql.transactional-lock=false",
    "payment.outbox.enabled=false"
})
class PaymentOutboxAtomicityIntegrationTest {

    @Autowired
    private PaymentFinalizer paymentFinalizer;

    @Autowired
    private TicketOrderCreationProcessor ticketOrderCreationProcessor;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private PaymentOutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentOutboxWriter outboxWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void paidGuestFinalization_commitsBusinessStateAndOneOutboxEvent_andReplayDoesNotDuplicate() {
        Long eventId = insertEvent(BigDecimal.valueOf(10000));
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(PaymentOrder.builder()
            .orderNo("ORDER-PAID-" + UUID.randomUUID())
            .buyerMemberId(null)
            .buyerName("guest")
            .buyerEmail("guest@example.com")
            .buyerPhone("010-1234-5678")
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.CARD)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(now.plusMinutes(10))
            .createdAt(now)
            .updatedAt(now)
            .build());
        TicketOrder ticketOrder = ticketOrderRepository.saveAndFlush(TicketOrder.builder()
            .paymentOrderId(paymentOrder.getId())
            .eventId(eventId)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(TicketOrderStatus.PENDING_PAYMENT.name())
            .createdAt(now)
            .updatedAt(now)
            .build());
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
            "payment-key-" + UUID.randomUUID(),
            paymentOrder.getOrderNo(),
            BigDecimal.valueOf(10000)
        );

        paymentFinalizer.finalizePayment(null, request, tossResponse(request));
        paymentFinalizer.finalizePayment(null, request, tossResponse(request));

        PaymentOrder reloadedPaymentOrder = paymentOrderRepository.findById(paymentOrder.getId()).orElseThrow();
        TicketOrder reloadedTicketOrder = ticketOrderRepository.findById(ticketOrder.getId()).orElseThrow();
        assertThat(paymentRepository.findByPaymentOrderId(paymentOrder.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID.name());
        assertThat(reloadedPaymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(reloadedTicketOrder.getStatus()).isEqualTo(TicketOrderStatus.CONFIRMED.name());
        assertOnePendingOutbox(paymentOrder.getOrderNo());
    }

    @Test
    void freeGuestCreation_commitsConfirmedTicketOrderAndOneOutboxEvent() {
        Long eventId = insertEvent(BigDecimal.ZERO);

        CreateTicketOrderResponse response = ticketOrderCreationProcessor.create(
            null,
            eventId,
            null,
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("guest", "guest@example.com", "010-1234-5678")
            )
        );

        TicketOrder ticketOrder = ticketOrderRepository.findById(response.getTicketOrderId()).orElseThrow();
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.CONFIRMED.name());
        assertOnePendingOutbox(response.getOrderNo());
    }

    @Test
    void writerUniqueConstraintPreventsDuplicateBusinessEvent() {
        outboxWriter.appendTicketReservationConfirmation("ORDER-DUP-1", "guest@example.com", "event");
        outboxWriter.appendTicketReservationConfirmation("ORDER-DUP-1", "guest@example.com", "event");

        assertThat(outboxEventRepository.findAll())
            .filteredOn(event -> event.getAggregateId().equals("ORDER-DUP-1"))
            .hasSize(1);
    }

    @Test
    void concurrentWriterDuplicateBusinessEventInsertsExactlyOneRow() throws Exception {
        String orderNo = "ORDER-CONCURRENT-DUP-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> write = () -> {
                outboxWriter.appendTicketReservationConfirmation(orderNo, "guest@example.com", "event");
                return null;
            };

            for (var future : executor.invokeAll(List.of(write, write))) {
                future.get();
            }

            assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> event.getAggregateId().equals(orderNo))
                .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void eventIdUniqueCollisionPropagates() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        outboxEventRepository.insertPending(
            eventId,
            PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL.name(),
            "ORDER-EVENT-ID-1",
            "{}",
            now
        );

        assertThatThrownBy(() -> outboxEventRepository.insertPending(
            eventId,
            PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL.name(),
            "ORDER-EVENT-ID-2",
            "{}",
            now
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonBusinessConstraintViolationPropagates() {
        assertThatThrownBy(() -> outboxEventRepository.insertPending(
            UUID.randomUUID(),
            PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL.name(),
            "O".repeat(65),
            "{}",
            OffsetDateTime.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonBusinessOutboxIntegrityFailureRollsBackBusinessTransaction() {
        String orderNo = "ORDER-INTEGRITY-ROLLBACK-" + UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            paymentOrderRepository.save(PaymentOrder.builder()
                .orderNo(orderNo)
                .buyerMemberId(null)
                .buyerName("guest")
                .buyerEmail("guest@example.com")
                .buyerPhone("010-1234-5678")
                .orderType(PaymentOrderType.EVENT_TICKET)
                .totalAmount(BigDecimal.valueOf(10000))
                .requestedPaymentMethod(PaymentMethod.CARD)
                .status(PaymentOrderStatus.PENDING.name())
                .expiresAt(now.plusMinutes(10))
                .createdAt(now)
                .updatedAt(now)
                .build());
            outboxWriter.appendTicketReservationConfirmation(
                UUID.randomUUID(),
                "O".repeat(65),
                "guest@example.com",
                "event"
            );
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(paymentOrderRepository.findByOrderNo(orderNo)).isEmpty();
    }

    private void assertOnePendingOutbox(String orderNo) {
        assertThat(outboxEventRepository.findAll())
            .filteredOn(event -> event.getEventType()
                == PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL)
            .filteredOn(event -> event.getAggregateId().equals(orderNo))
            .singleElement()
            .satisfies(event -> {
                PaymentOutboxEvent outboxEvent = (PaymentOutboxEvent) event;
                assertThat(outboxEvent.getStatus()).isEqualTo(PaymentOutboxEventStatus.PENDING);
                assertThat(outboxEvent.getPayload()).contains(orderNo, "guest@example.com");
            });
    }

    private TossConfirmResponse tossResponse(ConfirmPaymentRequest request) {
        return new TossConfirmResponse(
            request.getPaymentKey(),
            request.getOrderId(),
            request.getAmount(),
            "DONE",
            "CARD",
            OffsetDateTime.now().minusMinutes(1),
            OffsetDateTime.now()
        );
    }

    private Long insertEvent(BigDecimal ticketPrice) {
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
            "outbox-org-" + UUID.randomUUID(),
            "outbox-org@example.com"
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
                ticket_sales_start_at,
                ticket_sales_end_at,
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
                now(), now() + interval '1 day',
                now() - interval '1 hour', now() + interval '1 hour',
                ?, 100, 0, 5,
                'PUBLISHED', false, false, false, 0, now(), now())
            """,
            organizationId,
            "outbox-event-" + UUID.randomUUID(),
            ticketPrice
        );
    }

    private Long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
