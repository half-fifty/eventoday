package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.dto.ExchangeCodeRedemptionDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
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

    @Autowired
    private OrderAccessTokenProvider orderAccessTokenProvider;

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
    void redeem_sameCodeSequentialReplayReturnsExistingAdmissionTicket() {
        Long memberId = insertMember("redeem-sequential-replay");
        Long eventId = insertEvent("redeem-sequential-replay-event", "PUBLISHED", 1);
        Long requestId = insertExchangeCodeRequest(eventId, memberId);
        Long codeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            null,
            "910010-910010-910010",
            "ISSUED",
            OffsetDateTime.now().plusDays(1)
        );

        ExchangeCodeRedemptionDtos.RedemptionResponse first =
            service.redeem(request("910010-910010-910010"), actor(memberId));
        String firstQrToken = admissionTicketQrToken(codeId);
        ExchangeCodeRedemptionDtos.RedemptionResponse second =
            service.redeem(request("910010-910010-910010"), actor(memberId));

        assertThat(second.admissionTicketId()).isEqualTo(first.admissionTicketId());
        assertThat(second.exchangeCodeStatus().name()).isEqualTo("REDEEMED");
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(countDistinctQrTokens(codeId)).isEqualTo(1);
        assertThat(admissionTicketQrToken(codeId)).isEqualTo(firstQrToken);
    }

    @Test
    void redeemGuestOrderExchangeCode_sameCodeSequentialReplayReturnsExistingAdmissionTicket() {
        Long eventId = insertEvent("redeem-guest-sequential-replay-event", "PUBLISHED", 1);
        String orderNo = "guest-replay-" + System.nanoTime();
        Long ticketOrderId = insertGuestTicketOrder(eventId, orderNo);
        Long codeId = insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            null,
            "910011-910011-910011",
            "ISSUED"
        );
        String accessToken = orderAccessTokenProvider.create(orderNo, OffsetDateTime.now().plusDays(1));

        ExchangeCodeRedemptionDtos.RedemptionResponse first =
            service.redeemGuestOrderExchangeCode(orderNo, accessToken, codeId);
        String firstQrToken = admissionTicketQrToken(codeId);
        ExchangeCodeRedemptionDtos.RedemptionResponse second =
            service.redeemGuestOrderExchangeCode(orderNo, accessToken, codeId);

        assertThat(second.admissionTicketId()).isEqualTo(first.admissionTicketId());
        assertThat(second.exchangeCodeStatus().name()).isEqualTo("REDEEMED");
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(countDistinctQrTokens(codeId)).isEqualTo(1);
        assertThat(admissionTicketQrToken(codeId)).isEqualTo(firstQrToken);
    }

    @Test
    void redeem_sameCodeConcurrentlyConvergesToOneAdmissionTicket() throws Exception {
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

        int workers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> admissionTicketIds = new CopyOnWriteArrayList<>();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> redeemConcurrently(
                    ready,
                    start,
                    admissionTicketIds,
                    memberId
                )));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(admissionTicketIds).hasSize(workers);
        assertThat(admissionTicketIds.stream().distinct().count()).isEqualTo(1);
        assertThat(exchangeCodeStatus(codeId)).isEqualTo("REDEEMED");
        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(countDistinctQrTokens(codeId)).isEqualTo(1);
    }

    private void redeemConcurrently(
            CountDownLatch ready,
            CountDownLatch start,
            List<Long> admissionTicketIds,
            Long memberId) {
        try {
            ready.countDown();
            start.await();
            ExchangeCodeRedemptionDtos.RedemptionResponse response =
                service.redeem(request("910003-910003-910003"), actor(memberId));
            admissionTicketIds.add(response.admissionTicketId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void redeem_completedReplayDoesNotExposeExistingTicketToDifferentMember() {
        Long ownerId = insertMember("redeem-owner");
        Long otherId = insertMember("redeem-other");
        Long eventId = insertEvent("redeem-security-event", "PUBLISHED", 1);
        Long requestId = insertExchangeCodeRequest(eventId, ownerId);
        Long codeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            null,
            "910012-910012-910012",
            "ISSUED",
            OffsetDateTime.now().plusDays(1)
        );
        ExchangeCodeRedemptionDtos.RedemptionResponse ownerResponse =
            service.redeem(request("910012-910012-910012"), actor(ownerId));

        assertThatThrownBy(() -> service.redeem(request("910012-910012-910012"), actor(otherId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH);

        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(admissionTicketId(codeId)).isEqualTo(ownerResponse.admissionTicketId());
    }

    @Test
    void redeemGuestOrderExchangeCode_completedReplayRejectsOtherOrderToken() {
        Long eventId = insertEvent("redeem-guest-security-event", "PUBLISHED", 1);
        String ownerOrderNo = "guest-owner-" + System.nanoTime();
        String otherOrderNo = "guest-other-" + System.nanoTime();
        Long ownerTicketOrderId = insertGuestTicketOrder(eventId, ownerOrderNo);
        insertGuestTicketOrder(eventId, otherOrderNo);
        Long codeId = insertExchangeCodeForTicketOrder(
            eventId,
            ownerTicketOrderId,
            null,
            "910013-910013-910013",
            "ISSUED"
        );
        String ownerToken = orderAccessTokenProvider.create(ownerOrderNo, OffsetDateTime.now().plusDays(1));
        String otherToken = orderAccessTokenProvider.create(otherOrderNo, OffsetDateTime.now().plusDays(1));
        ExchangeCodeRedemptionDtos.RedemptionResponse ownerResponse =
            service.redeemGuestOrderExchangeCode(ownerOrderNo, ownerToken, codeId);

        assertThatThrownBy(() ->
            service.redeemGuestOrderExchangeCode(otherOrderNo, otherToken, codeId))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);

        assertThat(countAdmissionTickets(codeId)).isEqualTo(1);
        assertThat(admissionTicketId(codeId)).isEqualTo(ownerResponse.admissionTicketId());
    }

    @Test
    void redeem_redeemedCodeWithoutAdmissionTicketFailsConsistencyWithoutCreatingTicket() {
        Long memberId = insertMember("redeem-missing-ticket");
        Long eventId = insertEvent("redeem-missing-ticket-event", "PUBLISHED", 1);
        Long requestId = insertExchangeCodeRequest(eventId, memberId);
        Long codeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            memberId,
            "910014-910014-910014",
            "REDEEMED",
            OffsetDateTime.now().plusDays(1)
        );

        assertThatThrownBy(() -> service.redeem(request("910014-910014-910014"), actor(memberId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_DATA_INCONSISTENT);

        assertThat(countAdmissionTickets(codeId)).isZero();
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

    private Long insertGuestTicketOrder(Long eventId, String orderNo) {
        OffsetDateTime now = OffsetDateTime.now();
        Long paymentOrderId = jdbcTemplate.queryForObject("""
            INSERT INTO payment_orders (
                order_no, order_type, buyer_name, buyer_email, buyer_phone,
                total_amount, status, created_at, updated_at
            )
            VALUES (?, 'EVENT_TICKET', 'guest', ?, '01012345678',
                0, 'PAID', ?, ?)
            RETURNING id
            """,
            Long.class,
            orderNo,
            orderNo + "@example.com",
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

    private Long admissionTicketId(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM admission_tickets WHERE exchange_code_id = ?",
            Long.class,
            exchangeCodeId
        );
    }

    private Integer countDistinctQrTokens(Long exchangeCodeId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT qr_token) FROM admission_tickets WHERE exchange_code_id = ?",
            Integer.class,
            exchangeCodeId
        );
    }
}
