package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExchangeCodeRequestConcurrencyIntegrationTest {

    @Autowired
    private ExchangeCodeRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void approveAndRejectConcurrentRequest_allowsOnlyOneTransition() throws Exception {
        Long adminId = insertMember("concurrency-admin", "PLATFORM_ADMIN");
        Long requesterId = insertMember("concurrency-requester", "USER");
        Long eventId = insertEvent("concurrency-event");
        Long requestId = insertRequest(eventId, requesterId);
        AuthenticatedMemberDto admin = new AuthenticatedMemberDto(adminId, PlatformRole.PLATFORM_ADMIN);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> approve = executorService.submit(() -> {
                startLatch.await();
                return runReview(() -> service.approveRequest(requestId, admin));
            });
            Future<Boolean> reject = executorService.submit(() -> {
                startLatch.await();
                return runReview(() -> service.rejectRequest(requestId, "reason", admin));
            });

            startLatch.countDown();

            List<Boolean> results = List.of(approve.get(), reject.get());

            assertThat(results).containsExactlyInAnyOrder(true, false);
            String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exchange_code_requests WHERE id = ?",
                String.class,
                requestId
            );
            assertThat(status).isIn(
                ExchangeCodeRequestStatus.APPROVED.name(),
                ExchangeCodeRequestStatus.REJECTED.name()
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    private Boolean runReview(ReviewAction action) throws Exception {
        try {
            action.run();
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private Long insertMember(String suffix, String platformRole) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO members (
                email, nickname, oauth_provider, oauth_subject,
                platform_role, status, created_at, updated_at
            )
            VALUES (?, ?, 'GOOGLE', ?, ?, 'ACTIVE', ?, ?)
            RETURNING id
            """,
            Long.class,
            suffix + "@example.com",
            "nick-" + suffix,
            "oauth-" + suffix,
            platformRole,
            now,
            now
        );
    }

    private Long insertEvent(String name) {
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
                ?, ?, 0, 100, 0, 5, 'PUBLISHED', false, false, false,
                10, ?, ?)
            RETURNING id
            """,
            Long.class,
            organizationId,
            name,
            now.plusDays(1),
            now.plusDays(2),
            now,
            now
        );
    }

    private Long insertRequest(Long eventId, Long memberId) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, created_at
            )
            VALUES (?, ?, 3, 'purpose', 'REQUESTED', ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            memberId,
            OffsetDateTime.now()
        );
    }

    @FunctionalInterface
    private interface ReviewAction {
        void run() throws Exception;
    }
}
