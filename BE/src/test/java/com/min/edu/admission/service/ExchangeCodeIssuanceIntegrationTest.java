package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import({
    TestcontainersConfiguration.class,
    ExchangeCodeIssuanceIntegrationTest.EmailSenderTestConfig.class
})
@SpringBootTest
class ExchangeCodeIssuanceIntegrationTest {

    @Autowired
    private ExchangeCodeIssuanceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingEmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender.reset();
    }

    @Test
    void issue_generatesCodesSendsEmailAndMarksEmailed() {
        Long adminId = insertMember("issue-admin", "PLATFORM_ADMIN", "admin@example.com");
        Long requesterId = insertMember("issue-requester", "USER", "requester@example.com");
        Long eventId = insertEvent("issue-event");
        Long requestId = insertApprovedRequest(eventId, requesterId, 3);

        service.issue(requestId, admin(adminId));

        assertThat(countCodes(requestId)).isEqualTo(3);
        assertThat(status(requestId)).isEqualTo("ISSUED");
        assertThat(emailedAt(requestId)).isNotNull();
        assertThat(emailSender.sentCount()).isEqualTo(1);
        assertThat(emailSender.lastMessage().to()).isEqualTo("requester@example.com");
        assertThat(emailSender.lastMessage().content()).contains("issue-event");
    }

    @Test
    void issue_keepsIssuedCodesWhenEmailSendingFails() {
        Long adminId = insertMember("mail-fail-admin", "PLATFORM_ADMIN", "admin-fail@example.com");
        Long requesterId = insertMember("mail-fail-requester", "USER", "requester-fail@example.com");
        Long eventId = insertEvent("mail-fail-event");
        Long requestId = insertApprovedRequest(eventId, requesterId, 2);
        emailSender.failNext();

        assertThatThrownBy(() -> service.issue(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        assertThat(countCodes(requestId)).isEqualTo(2);
        assertThat(status(requestId)).isEqualTo("ISSUED");
        assertThat(emailedAt(requestId)).isNull();
    }

    @Test
    void issue_failsWhenCalledAgainAfterIssued() {
        Long adminId = insertMember("issued-admin", "PLATFORM_ADMIN", "admin-issued@example.com");
        Long requesterId = insertMember("issued-requester", "USER", "requester-issued@example.com");
        Long eventId = insertEvent("issued-event");
        Long requestId = insertApprovedRequest(eventId, requesterId, 1);
        service.issue(requestId, admin(adminId));
        emailSender.reset();

        assertThatThrownBy(() -> service.issue(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED);

        assertThat(countCodes(requestId)).isEqualTo(1);
        assertThat(emailSender.sentCount()).isZero();
    }

    @Test
    void issue_concurrentCallsGenerateCodesOnlyOnce() throws Exception {
        Long adminId = insertMember("concurrent-issue-admin", "PLATFORM_ADMIN", "admin-concurrent@example.com");
        Long requesterId = insertMember("concurrent-issue-requester", "USER", "requester-concurrent@example.com");
        Long eventId = insertEvent("concurrent-issue-event");
        Long requestId = insertApprovedRequest(eventId, requesterId, 3);
        AuthenticatedMemberDto admin = admin(adminId);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executorService.submit(() -> {
                startLatch.await();
                return runIssuance(() -> service.issue(requestId, admin));
            });
            Future<Boolean> second = executorService.submit(() -> {
                startLatch.await();
                return runIssuance(() -> service.issue(requestId, admin));
            });

            startLatch.countDown();

            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
            assertThat(countCodes(requestId)).isEqualTo(3);
            assertThat(status(requestId)).isEqualTo("ISSUED");
            assertThat(emailSender.sentCount()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void issue_canGenerateOneThousandCodes() {
        Long adminId = insertMember("bulk-admin", "PLATFORM_ADMIN", "bulk-admin@example.com");
        Long requesterId = insertMember("bulk-requester", "USER", "bulk-requester@example.com");
        Long eventId = insertEvent("bulk-event");
        Long requestId = insertApprovedRequest(eventId, requesterId, 1000);

        service.issue(requestId, admin(adminId));

        assertThat(countCodes(requestId)).isEqualTo(1000);
        assertThat(status(requestId)).isEqualTo("ISSUED");
        assertThat(emailSender.sentCount()).isEqualTo(1);
    }

    @Test
    void issue_failsWhenEventHasEndedAndKeepsRequestApproved() {
        Long adminId = insertMember("ended-admin", "PLATFORM_ADMIN", "admin-ended@example.com");
        Long requesterId = insertMember("ended-requester", "USER", "requester-ended@example.com");
        Long eventId = insertEvent("ended-event", OffsetDateTime.now().minusSeconds(1));
        Long requestId = insertApprovedRequest(eventId, requesterId, 2);

        assertThatThrownBy(() -> service.issue(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_ENDED);

        assertThat(countCodes(requestId)).isZero();
        assertThat(status(requestId)).isEqualTo("APPROVED");
        assertThat(emailedAt(requestId)).isNull();
        assertThat(emailSender.sentCount()).isZero();
    }

    @Test
    void resendEmail_sendsExistingCodesAndOnlyMarksEmailed() {
        Long adminId = insertMember("resend-admin", "PLATFORM_ADMIN", "admin-resend@example.com");
        Long requesterId = insertMember("resend-requester", "USER", "requester-resend@example.com");
        Long eventId = insertEvent("resend-event");
        Long requestId = insertIssuedRequest(eventId, requesterId, 2, null);
        Long firstCodeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "RESEND-AAAAAA-AAAAAA"
        );
        Long secondCodeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "RESEND-BBBBBB-BBBBBB"
        );

        service.resendEmail(requestId, admin(adminId));

        assertThat(codeIds(requestId)).containsExactly(firstCodeId, secondCodeId);
        assertThat(codes(requestId)).containsExactly(
            "RESEND-AAAAAA-AAAAAA",
            "RESEND-BBBBBB-BBBBBB"
        );
        assertThat(countCodes(requestId)).isEqualTo(2);
        assertThat(status(requestId)).isEqualTo("ISSUED");
        assertThat(emailedAt(requestId)).isNotNull();
        assertThat(emailSender.sentCount()).isEqualTo(1);
        assertThat(emailSender.lastMessage().content()).contains("RESEND-AAAAAA-AAAAAA");
    }

    @Test
    void resendEmail_failsWhenEmailAlreadySent() {
        Long adminId = insertMember("resent-admin", "PLATFORM_ADMIN", "admin-resent@example.com");
        Long requesterId = insertMember("resent-requester", "USER", "requester-resent@example.com");
        Long eventId = insertEvent("resent-event");
        Long requestId = insertIssuedRequest(eventId, requesterId, 1, OffsetDateTime.now());
        insertExchangeCodeForRequest(eventId, requestId, "RESENT-AAAAAA-AAAAAA");

        assertThatThrownBy(() -> service.resendEmail(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT);

        assertThat(emailSender.sentCount()).isZero();
    }

    @Test
    void resendEmail_failsWhenCodeCountDoesNotMatch() {
        Long adminId = insertMember("resend-count-admin", "PLATFORM_ADMIN", "admin-resend-count@example.com");
        Long requesterId = insertMember("resend-count-requester", "USER", "requester-resend-count@example.com");
        Long eventId = insertEvent("resend-count-event");
        Long requestId = insertIssuedRequest(eventId, requesterId, 2, null);
        insertExchangeCodeForRequest(eventId, requestId, "COUNT-AAAAAA-AAAAAA");

        assertThatThrownBy(() -> service.resendEmail(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT);

        assertThat(emailSender.sentCount()).isZero();
    }

    @Test
    void resendEmail_keepsIssuedCodesWhenEmailSendingFails() {
        Long adminId = insertMember("resend-fail-admin", "PLATFORM_ADMIN", "admin-resend-fail@example.com");
        Long requesterId = insertMember("resend-fail-requester", "USER", "requester-resend-fail@example.com");
        Long eventId = insertEvent("resend-fail-event");
        Long requestId = insertIssuedRequest(eventId, requesterId, 1, null);
        insertExchangeCodeForRequest(eventId, requestId, "FAIL-AAAAAA-AAAAAA");
        emailSender.failNext();

        assertThatThrownBy(() -> service.resendEmail(requestId, admin(adminId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        assertThat(countCodes(requestId)).isEqualTo(1);
        assertThat(status(requestId)).isEqualTo("ISSUED");
        assertThat(emailedAt(requestId)).isNull();
    }

    private Boolean runIssuance(IssuanceAction action) throws Exception {
        try {
            action.run();
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private Long insertMember(String suffix, String platformRole, String email) {
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
            email,
            "nick-" + suffix,
            "oauth-" + suffix,
            platformRole,
            now,
            now
        );
    }

    private Long insertEvent(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        return insertEvent(name, now.plusDays(2));
    }

    private Long insertEvent(String name, OffsetDateTime endAt) {
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
            endAt,
            now,
            now
        );
    }

    private Long insertApprovedRequest(Long eventId, Long memberId, int quantity) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, reviewed_by, reviewed_at, created_at
            )
            VALUES (?, ?, ?, 'purpose', 'APPROVED', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            memberId,
            quantity,
            memberId,
            now,
            now
        );
    }

    private Long insertIssuedRequest(
            Long eventId,
            Long memberId,
            int quantity,
            OffsetDateTime emailedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, reviewed_by, reviewed_at, emailed_at, created_at
            )
            VALUES (?, ?, ?, 'purpose', 'ISSUED', ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            memberId,
            quantity,
            memberId,
            now,
            emailedAt,
            now
        );
    }

    private Long insertExchangeCodeForRequest(Long eventId, Long requestId, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_codes (
                event_id, exchange_code_request_id, code, status,
                expires_at, created_at, updated_at
            )
            VALUES (?, ?, ?, 'ISSUED', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            requestId,
            code,
            now.plusDays(1),
            now,
            now
        );
    }

    private long countCodes(Long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM exchange_codes WHERE exchange_code_request_id = ?",
            Long.class,
            requestId
        );
    }

    private String status(Long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM exchange_code_requests WHERE id = ?",
            String.class,
            requestId
        );
    }

    private OffsetDateTime emailedAt(Long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT emailed_at FROM exchange_code_requests WHERE id = ?",
            OffsetDateTime.class,
            requestId
        );
    }

    private List<Long> codeIds(Long requestId) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM exchange_codes WHERE exchange_code_request_id = ? ORDER BY id ASC",
            Long.class,
            requestId
        );
    }

    private List<String> codes(Long requestId) {
        return jdbcTemplate.queryForList(
            "SELECT code FROM exchange_codes WHERE exchange_code_request_id = ? ORDER BY id ASC",
            String.class,
            requestId
        );
    }

    private AuthenticatedMemberDto admin(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.PLATFORM_ADMIN);
    }

    @FunctionalInterface
    private interface IssuanceAction {
        void run() throws Exception;
    }

    @TestConfiguration
    static class EmailSenderTestConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    static class RecordingEmailSender implements EmailSender {
        private final AtomicInteger sentCount = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private volatile EmailMessage lastMessage;

        @Override
        public void send(EmailMessage message) {
            if (failNext.getAndSet(false)) {
                throw new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED);
            }
            lastMessage = message;
            sentCount.incrementAndGet();
        }

        void failNext() {
            failNext.set(true);
        }

        void reset() {
            sentCount.set(0);
            failNext.set(false);
            lastMessage = null;
        }

        int sentCount() {
            return sentCount.get();
        }

        EmailMessage lastMessage() {
            return lastMessage;
        }
    }
}
