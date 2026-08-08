package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.dto.ExchangeCodeRedemptionDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExchangeCodeRedemptionIntegrationTest {

    @Autowired
    private ExchangeCodeRedemptionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redeem_externalCodeAssignsHolderRedeemsAndCreatesAdmissionTicket() {
        Long memberId = insertMember("redeem-external");
        Long eventId = insertEvent("redeem-external-event", "PUBLISHED", 1);
        Long requestId = insertExchangeCodeRequest(eventId, memberId);
        Long codeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            null,
            "910001-910001-910001",
            "ISSUED",
            OffsetDateTime.now().plusDays(1)
        );

        ExchangeCodeRedemptionDtos.RedemptionResponse response =
            service.redeem(request("910001-910001-910001"), actor(memberId));

        assertThat(response.exchangeCodeId()).isEqualTo(codeId);
        assertThat(response.exchangeCodeStatus().name()).isEqualTo("REDEEMED");
        assertThat(response.admissionTicketId()).isNotNull();
        assertThat(response.qrAvailable()).isTrue();
        assertThat(exchangeCodeStatus(codeId)).isEqualTo("REDEEMED");
        assertThat(exchangeCodeHolder(codeId)).isEqualTo(memberId);
        assertThat(exchangeCodeRedeemedAt(codeId)).isNotNull();
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(admissionTicketQrToken(codeId)).isNotBlank();
    }

    @Test
    void redeem_failsBeforeMutatingWhenAdmissionTicketAlreadyExists() {
        Long memberId = insertMember("redeem-existing-ticket");
        Long eventId = insertEvent("redeem-existing-ticket-event", "PUBLISHED", 1);
        Long ticketOrderId = insertTicketOrder(eventId);
        Long codeId = insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            memberId,
            "910002-910002-910002",
            "ISSUED"
        );
        insertAdmissionTicket(codeId, memberId, "existing-qr-token");

        assertThatThrownBy(() -> service.redeem(request("910002-910002-910002"), actor(memberId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_ALREADY_EXISTS);

        assertThat(exchangeCodeStatus(codeId)).isEqualTo("ISSUED");
        assertThat(exchangeCodeRedeemedAt(codeId)).isNull();
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
    }

    @Test
    void redeem_sameCodeConcurrentlySucceedsOnlyOnce() throws Exception {
        Long memberId = insertMember("redeem-concurrent");
        Long eventId = insertEvent("redeem-concurrent-event", "PUBLISHED", 1);
        Long requestId = insertExchangeCodeRequest(eventId, memberId);
        Long codeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            null,
            "910003-910003-910003",
            "ISSUED",
            OffsetDateTime.now().plusDays(1)
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Future<?> first = executor.submit(() -> redeemConcurrently(
            ready,
            start,
            successCount,
            conflictCount,
            memberId
        ));
        Future<?> second = executor.submit(() -> redeemConcurrently(
            ready,
            start,
            successCount,
            conflictCount,
            memberId
        ));

        ready.await();
        start.countDown();
        first.get();
        second.get();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        assertThat(exchangeCodeStatus(codeId)).isEqualTo("REDEEMED");
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
    }

    private void redeemConcurrently(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successCount,
            AtomicInteger conflictCount,
            Long memberId) {
        try {
            ready.countDown();
            start.await();
            service.redeem(request("910003-910003-910003"), actor(memberId));
            successCount.incrementAndGet();
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE) {
                conflictCount.incrementAndGet();
                return;
            }
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ExchangeCodeRedemptionDtos.Request request(String code) {
        return new ExchangeCodeRedemptionDtos.Request(code);
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
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

    private Long insertEvent(String name, String status, int endAtOffsetDays) {
        OffsetDateTime now = OffsetDateTime.now();
        Long organizationId = jdbcTemplate.queryForObject("""
            INSERT INTO organizations (
                organization_type, name, contact_email, contact_phone,
                status, created_at, updated_at
            )
            VALUES ('SOLE_PROPRIETOR', ?, ?, '01012345678',
                'ACTIVE', ?, ?)
            RETURNING id
            """,
            Long.class,
            "org-" + name,
            "org-" + name + "@example.com",
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
            VALUES (?, ?, 'CONFERENCE', 'description', 'venue', 'address',
                ?, ?, 0, 100, 0, 5, ?, false, false, false,
                10, ?, ?)
            RETURNING id
            """,
            Long.class,
            organizationId,
            name,
            now.minusDays(1),
            now.plusDays(endAtOffsetDays),
            status,
            now,
            now
        );
    }

    private Long insertExchangeCodeRequest(Long eventId, Long memberId) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, created_at
            )
            VALUES (?, ?, 1, 'purpose', 'ISSUED', ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            memberId,
            OffsetDateTime.now()
        );
    }

    private Long insertExchangeCodeForRequest(
            Long eventId,
            Long requestId,
            Long holderMemberId,
            String code,
            String status,
            OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_codes (
                event_id, exchange_code_request_id, holder_member_id, code, status,
                expires_at, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            requestId,
            holderMemberId,
            code,
            status,
            expiresAt,
            now,
            now
        );
    }

    private Long insertExchangeCodeForTicketOrder(
            Long eventId,
            Long ticketOrderId,
            Long holderMemberId,
            String code,
            String status) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_codes (
                event_id, ticket_order_id, holder_member_id, code, status,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            ticketOrderId,
            holderMemberId,
            code,
            status,
            now,
            now
        );
    }

    private Long insertTicketOrder(Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        Long paymentOrderId = jdbcTemplate.queryForObject("""
            INSERT INTO payment_orders (
                order_no, order_type, total_amount, status, created_at, updated_at
            )
            VALUES (?, 'EVENT_TICKET', 0, 'PAID', ?, ?)
            RETURNING id
            """,
            Long.class,
            "order-" + eventId + "-" + System.nanoTime(),
            now,
            now
        );

        return jdbcTemplate.queryForObject("""
            INSERT INTO ticket_orders (
                payment_order_id, event_id, unit_price, total_quantity,
                status, confirmed_at, created_at, updated_at
            )
            VALUES (?, ?, 0, 1, 'CONFIRMED', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            paymentOrderId,
            eventId,
            now,
            now,
            now
        );
    }

    private Long insertAdmissionTicket(Long exchangeCodeId, Long memberId, String qrToken) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO admission_tickets (
                exchange_code_id, member_id, qr_token, status, issued_at
            )
            VALUES (?, ?, ?, 'ISSUED', ?)
            RETURNING id
            """,
            Long.class,
            exchangeCodeId,
            memberId,
            qrToken,
            now
        );
    }

    private String exchangeCodeStatus(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM exchange_codes WHERE id = ?",
            String.class,
            exchangeCodeId
        );
    }

    private Long exchangeCodeHolder(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT holder_member_id FROM exchange_codes WHERE id = ?",
            Long.class,
            exchangeCodeId
        );
    }

    private OffsetDateTime exchangeCodeRedeemedAt(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT redeemed_at FROM exchange_codes WHERE id = ?",
            OffsetDateTime.class,
            exchangeCodeId
        );
    }

    private Integer countAdmissionTickets(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admission_tickets WHERE exchange_code_id = ?",
            Integer.class,
            exchangeCodeId
        );
    }

    private String admissionTicketQrToken(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT qr_token FROM admission_tickets WHERE exchange_code_id = ?",
            String.class,
            exchangeCodeId
        );
    }
}
