package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExchangeCodeRequestCreationConcurrencyIntegrationTest {

    @Autowired
    private ExchangeCodeRequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createRequest_translatesPostgresPartialUniqueViolationToBusinessConflict()
            throws Exception {
        Long managerId = insertMember("race-manager", "USER");
        Long eventId = insertEventWithManager("race-event", managerId);
        Long requesterId = insertMember("race-requester", "USER");
        CountDownLatch insertedButUncommitted = new CountDownLatch(1);
        CountDownLatch serviceStarted = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<Void> blocker = executorService.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    insertRequest(eventId, requesterId);
                    insertedButUncommitted.countDown();
                    await(serviceStarted);
                    sleepBriefly();
                });
                return null;
            });

            insertedButUncommitted.await();

            Future<GlobalErrorCode> serviceResult = executorService.submit(() -> {
                try {
                    serviceStarted.countDown();
                    service.createRequest(
                        eventId,
                        new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
                        new AuthenticatedMemberDto(managerId, PlatformRole.USER)
                    );
                    return null;
                } catch (BusinessException exception) {
                    return exception.getErrorCode();
                }
            });

            blocker.get();

            assertThat(serviceResult.get())
                .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_EXISTS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
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

    private Long insertEventWithManager(String name, Long managerId) {
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
        jdbcTemplate.update("""
            INSERT INTO organization_members (
                organization_id, member_id, organization_role, status, joined_at
            )
            VALUES (?, ?, 'MANAGER', 'ACTIVE', ?)
            """,
            organizationId,
            managerId,
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

    private void insertRequest(Long eventId, Long memberId) {
        jdbcTemplate.update("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, created_at
            )
            VALUES (?, ?, 3, 'purpose', 'REQUESTED', ?)
            """,
            eventId,
            memberId,
            OffsetDateTime.now()
        );
    }
}
