package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.boot.test.context.TestConfiguration;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
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
    PaymentFinalizationRollbackIntegrationTest.FailingFinalizationConfig.class
})
@SpringBootTest
class PaymentFinalizationRollbackIntegrationTest {

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

    @Test
    void confirm_rollsBackAllLocalChangesWhenExchangeCodeIssueFails() {
        Long memberId = insertMember();
        Long eventId = insertEvent();
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOrder paymentOrder = paymentOrderRepository.saveAndFlush(
            PaymentOrder.builder()
                .orderNo("ORDER-ROLLBACK-" + UUID.randomUUID())
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

        assertThatThrownBy(() -> paymentConfirmService.confirm(
            memberId,
            null,
            new ConfirmPaymentRequest(
                "payment-key-" + UUID.randomUUID(),
                paymentOrder.getOrderNo(),
                BigDecimal.valueOf(10000)
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);

        assertThat(paymentRepository.findByPaymentOrderId(paymentOrder.getId())).isEmpty();
        PaymentOrder reloadedPaymentOrder = paymentOrderRepository
            .findById(paymentOrder.getId())
            .orElseThrow();
        TicketOrder reloadedTicketOrder = ticketOrderRepository
            .findById(ticketOrder.getId())
            .orElseThrow();
        assertThat(reloadedPaymentOrder.getStatus())
            .isEqualTo(PaymentOrderStatus.PENDING.name());
        assertThat(reloadedTicketOrder.getStatus())
            .isEqualTo(TicketOrderStatus.PENDING_PAYMENT.name());
        assertThat(reloadedTicketOrder.getConfirmedAt()).isNull();
        Integer exchangeCodeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM exchange_codes WHERE ticket_order_id = ?",
            Integer.class,
            ticketOrder.getId()
        );
        assertThat(exchangeCodeCount).isZero();
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
            "rollback-" + suffix + "@example.com",
            "rollback-" + suffix,
            "rollback-subject-" + suffix
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
            "rollback-org-" + UUID.randomUUID(),
            "rollback-org@example.com"
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
            "rollback-event-" + UUID.randomUUID()
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

    @TestConfiguration
    static class FailingFinalizationConfig {

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

                @Override
                public TossCancelResponse cancel(TossCancelRequest request) {
                    throw new UnsupportedOperationException("Not used in this test.");
                }
            };
        }

        @Bean
        @Primary
        TicketExchangeCodeIssuer ticketExchangeCodeIssuer() {
            return new TicketExchangeCodeIssuer(null, null) {
                @Override
                public List<ExchangeCode> issueIfAbsent(
                        TicketOrder ticketOrder,
                        Long holderMemberId,
                        OffsetDateTime now) {
                    throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
                }
            };
        }
    }
}
