package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.TossPaymentWebhookRequest;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;

@Import({
    TestcontainersConfiguration.class,
    PaymentWebhookIntegrationTest.FakeTossPaymentClientConfig.class
})
@SpringBootTest
class PaymentWebhookIntegrationTest {

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

    @AfterEach
    void tearDown() {
        fakeTossPaymentClient.clear();
    }

    @Test
    void webhook_recoversLocalPendingOrderWhenTossIsDone() {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture order = createPendingOrder(memberId, eventId, "WEBHOOK-RECOVERY");
        String paymentKey = "payment-key-" + UUID.randomUUID();
        TossConfirmResponse tossPayment = tossPayment(paymentKey, order.orderNo());
        fakeTossPaymentClient.put(paymentKey, tossPayment);

        paymentWebhookService.handleTossWebhook(webhook(tossPayment));

        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertOrderStatus(order.paymentOrderId(), PaymentOrderStatus.PAID);
        assertTicketOrderStatus(order.paymentOrderId(), TicketOrderStatus.CONFIRMED);
        assertExchangeCodeCount(order.ticketOrderId(), 1);
    }

    @Test
    void webhook_isIdempotentWhenSameDoneEventIsReceivedAgain() {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture order = createPendingOrder(memberId, eventId, "WEBHOOK-IDEMPOTENT");
        String paymentKey = "payment-key-" + UUID.randomUUID();
        TossConfirmResponse tossPayment = tossPayment(paymentKey, order.orderNo());
        fakeTossPaymentClient.put(paymentKey, tossPayment);

        paymentWebhookService.handleTossWebhook(webhook(tossPayment));
        paymentWebhookService.handleTossWebhook(webhook(tossPayment));

        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertExchangeCodeCount(order.ticketOrderId(), 1);
    }

    @Test
    void webhook_recoversExpiredLocalOrderWhenTossIsAlreadyDone() {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OrderFixture order = createExpiredPendingOrder(memberId, eventId, "WEBHOOK-EXPIRED");
        String paymentKey = "payment-key-" + UUID.randomUUID();
        TossConfirmResponse tossPayment = tossPayment(paymentKey, order.orderNo());
        fakeTossPaymentClient.put(paymentKey, tossPayment);

        paymentWebhookService.handleTossWebhook(webhook(tossPayment));

        assertThat(paymentRepository.countByPaymentKey(paymentKey)).isEqualTo(1);
        assertOrderStatus(order.paymentOrderId(), PaymentOrderStatus.PAID);
        assertTicketOrderStatus(order.paymentOrderId(), TicketOrderStatus.CONFIRMED);
        assertExchangeCodeCount(order.ticketOrderId(), 1);
    }

    private TossPaymentWebhookRequest webhook(TossConfirmResponse tossPayment) {
        return new TossPaymentWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            "2026-08-04T11:20:00.123456",
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

    private TossConfirmResponse tossPayment(String paymentKey, String orderNo) {
        return new TossConfirmResponse(
            paymentKey,
            orderNo,
            BigDecimal.valueOf(10000),
            "DONE",
            "CARD",
            OffsetDateTime.now().minusMinutes(2),
            OffsetDateTime.now().minusMinutes(1)
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

    private OrderFixture createExpiredPendingOrder(
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
                .expiresAt(now.minusSeconds(1))
                .createdAt(now.minusMinutes(10))
                .updatedAt(now.minusMinutes(10))
                .build()
        );
        TicketOrder ticketOrder = ticketOrderRepository.saveAndFlush(
            TicketOrder.builder()
                .paymentOrderId(paymentOrder.getId())
                .eventId(eventId)
                .unitPrice(BigDecimal.valueOf(10000))
                .totalQuantity(1)
                .status(TicketOrderStatus.PENDING_PAYMENT.name())
                .createdAt(now.minusMinutes(10))
                .updatedAt(now.minusMinutes(10))
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

    private record OrderFixture(
            Long paymentOrderId,
            Long ticketOrderId,
            String orderNo
    ) {
    }

    static class FakeTossPaymentClient implements TossPaymentClient {

        private final Map<String, TossConfirmResponse> payments = new ConcurrentHashMap<>();

        @Override
        public TossConfirmResponse confirm(TossConfirmRequest request) {
            return payments.get(request.paymentKey());
        }

        @Override
        public TossConfirmResponse getPayment(String paymentKey) {
            return payments.get(paymentKey);
        }

        @Override
        public TossCancelResponse cancel(TossCancelRequest request) {
            throw new UnsupportedOperationException("Not used in this test.");
        }

        void put(String paymentKey, TossConfirmResponse response) {
            payments.put(paymentKey, response);
        }

        void clear() {
            payments.clear();
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
