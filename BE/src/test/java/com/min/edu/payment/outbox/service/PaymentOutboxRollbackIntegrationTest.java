package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.UUID;

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
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.service.PaymentFinalizer;
import com.min.edu.payment.service.TicketOrderCreationProcessor;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@Import({
    TestcontainersConfiguration.class,
    PaymentOutboxRollbackIntegrationTest.FailingOutboxWriterConfiguration.class
})
@SpringBootTest(properties = {
    "spring.flyway.postgresql.transactional-lock=false",
    "payment.outbox.enabled=false"
})
class PaymentOutboxRollbackIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void paidGuestRollbackAlsoRollsBackOutboxInsert() {
        Long eventId = insertEvent(BigDecimal.valueOf(10000));
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(PaymentOrder.builder()
            .orderNo("ORDER-ROLLBACK-" + UUID.randomUUID())
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

        assertThatThrownBy(() -> paymentFinalizer.finalizePayment(null, request, tossResponse(request)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("forced outbox failure");

        assertThat(paymentRepository.findByPaymentOrderId(paymentOrder.getId())).isEmpty();
        assertThat(paymentOrderRepository.findById(paymentOrder.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentOrderStatus.PENDING.name());
        TicketOrder reloadedTicketOrder = ticketOrderRepository.findById(ticketOrder.getId()).orElseThrow();
        assertThat(reloadedTicketOrder.getStatus()).isEqualTo(TicketOrderStatus.PENDING_PAYMENT.name());
        assertThat(reloadedTicketOrder.getConfirmedAt()).isNull();
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    @Test
    void freeGuestRollbackAlsoRollsBackOutboxInsert() {
        Long eventId = insertEvent(BigDecimal.ZERO);
        Integer paymentOrderCountBefore = count("payment_orders");
        Integer ticketOrderCountBefore = count("ticket_orders");

        assertThatThrownBy(() -> ticketOrderCreationProcessor.create(
            null,
            eventId,
            null,
            new CreateTicketOrderRequest(
                1,
                new GuestBuyerRequest("guest", "guest@example.com", "010-1234-5678")
            )
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("forced outbox failure");

        assertThat(count("payment_orders")).isEqualTo(paymentOrderCountBefore);
        assertThat(count("ticket_orders")).isEqualTo(ticketOrderCountBefore);
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    private Integer count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
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
            "rollback-outbox-org-" + UUID.randomUUID(),
            "rollback-outbox-org@example.com"
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
            "rollback-outbox-event-" + UUID.randomUUID(),
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

    @TestConfiguration
    static class FailingOutboxWriterConfiguration {

        @Bean
        @Primary
        PaymentOutboxWriter paymentOutboxWriter(PaymentOutboxEventRepository repository) {
            return new PaymentOutboxWriter(repository, new tools.jackson.databind.ObjectMapper()) {
                @Override
                public void appendTicketReservationConfirmation(
                        String orderNo,
                        String buyerEmail,
                        String eventName) {
                    repository.save(PaymentOutboxEvent.ticketReservationConfirmation(
                        orderNo,
                        "{\"orderNo\":\"" + orderNo + "\",\"buyerEmail\":\"" + buyerEmail
                            + "\",\"eventName\":\"" + eventName + "\"}",
                        OffsetDateTime.now()
                    ));
                    throw new IllegalStateException("forced outbox failure");
                }
            };
        }
    }
}
