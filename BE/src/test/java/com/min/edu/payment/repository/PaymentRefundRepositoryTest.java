package com.min.edu.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.min.edu.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentRefundRepositoryTest {

    @Autowired
    private PaymentRefundRepository paymentRefundRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findMyRefunds_countsSameEventTicketRowsAsContent() {
        Long memberId = insertMember("member");
        Long eventId = insertEvent();
        Long eventTicketPaymentId = insertPaidOrderWithPayment(
            memberId,
            "EVENT_TICKET",
            "ORDER-TICKET"
        );
        insertTicketOrder(eventTicketPaymentId, eventId);
        insertRefund(eventTicketPaymentId);

        Long eventAdPaymentId = insertPaidOrderWithPayment(
            memberId,
            "EVENT_AD",
            "ORDER-AD"
        );
        insertRefund(eventAdPaymentId);

        Page<RefundListProjection> page =
            paymentRefundRepository.findMyRefunds(memberId, PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getContent().get(0).getOrderNo()).isEqualTo("ORDER-TICKET");
    }

    private Long insertMember(String suffix) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO members (
                email, nickname, oauth_provider, oauth_subject,
                platform_role, status, created_at, updated_at
            )
            VALUES (?, ?, 'GOOGLE', ?, 'USER', 'ACTIVE', ?, ?)
            RETURNING id
            """,
            Long.class,
            suffix + "@example.com",
            "nick-" + suffix,
            "oauth-" + suffix,
            now,
            now
        );
    }

    private Long insertEvent() {
        OffsetDateTime now = OffsetDateTime.now();
        Long organizationId = jdbcTemplate.queryForObject("""
            INSERT INTO organizations (
                organization_type, name, contact_email, contact_phone,
                status, created_at, updated_at
            )
            VALUES ('SOLE_PROPRIETOR', 'org', 'org@example.com', '01012345678',
                'ACTIVE', ?, ?)
            RETURNING id
            """,
            Long.class,
            now,
            now
        );

        return jdbcTemplate.queryForObject("""
            INSERT INTO events (
                organizer_organization_id, name, event_type, description,
                venue_name, address, start_at, end_at,
                ticket_price, ticket_total_quantity, ticket_sold_quantity,
                ticket_purchase_limit, status, booth_recruitment_enabled,
                venue_map_enabled, booth_reservation_enabled,
                no_show_grace_minutes, created_at, updated_at
            )
            VALUES (?, 'event', 'CONFERENCE', 'description', 'venue', 'address',
                ?, ?, 10000, 100, 1, 5, 'PUBLISHED', false, false, false,
                10, ?, ?)
            RETURNING id
            """,
            Long.class,
            organizationId,
            now.plusDays(1),
            now.plusDays(2),
            now,
            now
        );
    }

    private Long insertPaidOrderWithPayment(
            Long memberId,
            String orderType,
            String orderNo) {
        OffsetDateTime now = OffsetDateTime.now();
        Long paymentOrderId = jdbcTemplate.queryForObject("""
            INSERT INTO payment_orders (
                order_no, buyer_member_id, order_type, total_amount,
                status, expires_at, created_at, updated_at
            )
            VALUES (?, ?, ?, 10000, 'PAID', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            orderNo,
            memberId,
            orderType,
            now.plusMinutes(10),
            now,
            now
        );

        return jdbcTemplate.queryForObject("""
            INSERT INTO payments (
                payment_order_id, pg_provider, payment_key, method, amount,
                status, requested_at, approved_at, updated_at
            )
            VALUES (?, 'TOSS_PAYMENTS', ?, 'CARD', 10000, 'PAID', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            paymentOrderId,
            "payment-key-" + orderNo,
            now,
            now,
            now
        );
    }

    private void insertTicketOrder(Long paymentId, Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        Long paymentOrderId = jdbcTemplate.queryForObject(
            "SELECT payment_order_id FROM payments WHERE id = ?",
            Long.class,
            paymentId
        );
        jdbcTemplate.update("""
            INSERT INTO ticket_orders (
                payment_order_id, event_id, unit_price, total_quantity,
                status, confirmed_at, created_at, updated_at
            )
            VALUES (?, ?, 10000, 1, 'CONFIRMED', ?, ?, ?)
            """,
            paymentOrderId,
            eventId,
            now,
            now,
            now
        );
    }

    private void insertRefund(Long paymentId) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO payment_refunds (
                payment_id, refund_amount, reason, status,
                requested_at, completed_at, pg_cancel_key
            )
            VALUES (?, 10000, 'reason', 'COMPLETED', ?, ?, ?)
            """,
            paymentId,
            now,
            now,
            "cancel-key-" + paymentId
        );
    }
}
