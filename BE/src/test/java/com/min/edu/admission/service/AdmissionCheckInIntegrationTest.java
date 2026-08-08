package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.dto.AdmissionCheckInDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdmissionCheckInIntegrationTest {

    @Autowired
    private AdmissionCheckInService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void checkIn_changesTicketToUsedAndWritesSuccessLog() {
        Long staffId = insertMember("checkin-staff-success");
        Long holderId = insertMember("checkin-holder-success");
        Long organizationId = insertOrganization("checkin-org-success");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "checkin-event-success", "PUBLISHED", 1);
        Long ticketId = insertIssuedAdmissionTicket(eventId, holderId, "qr-checkin-success");

        AdmissionCheckInDtos.CheckInResponse response = service.checkIn(
            eventId,
            new AdmissionCheckInDtos.CheckInRequest("qr-checkin-success", "A Gate"),
            actor(staffId)
        );

        assertThat(response.admissionTicketId()).isEqualTo(ticketId);
        assertThat(response.status().name()).isEqualTo("USED");
        assertThat(response.result()).isEqualTo(AdmissionResult.SUCCESS);
        assertThat(ticketStatus(ticketId)).isEqualTo("USED");
        assertThat(ticketUsedAt(ticketId)).isNotNull();
        assertThat(countLogs(ticketId, "CHECK_IN", "SUCCESS")).isEqualTo(1);
        assertThat(logGateName(ticketId)).isEqualTo("A Gate");
    }

    @Test
    void duplicateCheckIn_writesDuplicateLogAndReturnsConflict() {
        Long staffId = insertMember("checkin-staff-duplicate");
        Long holderId = insertMember("checkin-holder-duplicate");
        Long organizationId = insertOrganization("checkin-org-duplicate");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "checkin-event-duplicate", "PUBLISHED", 1);
        Long ticketId = insertUsedAdmissionTicket(eventId, holderId, "qr-checkin-duplicate");

        assertThatThrownBy(() -> service.checkIn(
            eventId,
            new AdmissionCheckInDtos.CheckInRequest("qr-checkin-duplicate", null),
            actor(staffId)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_DUPLICATE);

        assertThat(ticketStatus(ticketId)).isEqualTo("USED");
        assertThat(countLogs(ticketId, "CHECK_IN", "DUPLICATE")).isEqualTo(1);
    }

    @Test
    void invalidCheckIn_writesInvalidLogAndReturnsConflict() {
        Long staffId = insertMember("checkin-staff-invalid");
        Long holderId = insertMember("checkin-holder-invalid");
        Long organizationId = insertOrganization("checkin-org-invalid");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "checkin-event-invalid", "PUBLISHED", 1);
        Long ticketId = insertAdmissionTicket(eventId, holderId, "qr-checkin-invalid", "CANCELLED");

        assertThatThrownBy(() -> service.checkIn(
            eventId,
            new AdmissionCheckInDtos.CheckInRequest("qr-checkin-invalid", null),
            actor(staffId)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_INVALID_STATE);

        assertThat(ticketStatus(ticketId)).isEqualTo("CANCELLED");
        assertThat(countLogs(ticketId, "CHECK_IN", "INVALID")).isEqualTo(1);
    }

    @Test
    void checkIn_rejectsMissingQrAndEventMismatchWithoutLog() {
        Long staffId = insertMember("checkin-staff-mismatch");
        Long holderId = insertMember("checkin-holder-mismatch");
        Long organizationId = insertOrganization("checkin-org-mismatch");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "checkin-event-mismatch", "PUBLISHED", 1);
        Long otherEventId = insertEvent(organizationId, "checkin-other-event", "PUBLISHED", 1);
        Long ticketId = insertIssuedAdmissionTicket(otherEventId, holderId, "qr-checkin-mismatch");

        assertThatThrownBy(() -> service.checkIn(
            eventId,
            new AdmissionCheckInDtos.CheckInRequest("not-found-token", null),
            actor(staffId)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);

        assertThatThrownBy(() -> service.checkIn(
            eventId,
            new AdmissionCheckInDtos.CheckInRequest("qr-checkin-mismatch", null),
            actor(staffId)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_EVENT_MISMATCH);

        assertThat(countAllLogs(ticketId)).isZero();
    }

    @Test
    void checkInCancel_restoresUsedTicketAndWritesCancelLogAfterEventEnded() {
        Long staffId = insertMember("cancel-staff-success");
        Long holderId = insertMember("cancel-holder-success");
        Long organizationId = insertOrganization("cancel-org-success");
        Long eventId = insertEvent(organizationId, "cancel-event-success", "PUBLISHED", -1);
        insertEventMemberAllowedStaffFixture(eventId, staffId);
        Long ticketId = insertUsedAdmissionTicket(eventId, holderId, "qr-cancel-success");

        AdmissionCheckInDtos.CheckInCancellationResponse response =
            service.cancelCheckIn(eventId, ticketId, actor(staffId));

        assertThat(response.status().name()).isEqualTo("ISSUED");
        assertThat(response.usedAt()).isNull();
        assertThat(ticketStatus(ticketId)).isEqualTo("ISSUED");
        assertThat(ticketUsedAt(ticketId)).isNull();
        assertThat(ticketCancelledAt(ticketId)).isNull();
        assertThat(countLogs(ticketId, "CHECK_IN_CANCEL", "SUCCESS")).isEqualTo(1);
    }

    @Test
    void cancelCheckIn_invalidStateWritesInvalidLogAndReturnsConflict() {
        Long staffId = insertMember("cancel-staff-invalid");
        Long holderId = insertMember("cancel-holder-invalid");
        Long organizationId = insertOrganization("cancel-org-invalid");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "cancel-event-invalid", "PUBLISHED", 1);
        Long ticketId = insertIssuedAdmissionTicket(eventId, holderId, "qr-cancel-invalid");

        assertThatThrownBy(() -> service.cancelCheckIn(eventId, ticketId, actor(staffId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_CANCEL_INVALID_STATE);

        assertThat(ticketStatus(ticketId)).isEqualTo("ISSUED");
        assertThat(countLogs(ticketId, "CHECK_IN_CANCEL", "INVALID")).isEqualTo(1);
    }

    @Test
    void eventAdmissionLogs_filtersSortsAndRejectsCheckinStaff() {
        Long managerId = insertMember("log-manager");
        Long staffId = insertMember("log-staff");
        Long holderId = insertMember("log-holder");
        Long organizationId = insertOrganization("log-org");
        insertOrganizationMember(organizationId, managerId, "MANAGER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "log-event", "PUBLISHED", 1);
        insertEventMember(eventId, staffId, "CHECKIN_STAFF", true);
        Long ticketId = insertUsedAdmissionTicket(eventId, holderId, "qr-log");
        Long oldLogId = insertAdmissionLog(
            ticketId,
            staffId,
            "CHECK_IN",
            "SUCCESS",
            "A Gate",
            OffsetDateTime.now().minusMinutes(1)
        );
        Long latestLogId = insertAdmissionLog(
            ticketId,
            staffId,
            "CHECK_IN_CANCEL",
            "SUCCESS",
            null,
            OffsetDateTime.now()
        );

        Page<AdmissionCheckInDtos.LogListResponse> all = service.getEventAdmissionLogs(
            eventId,
            null,
            AdmissionResult.SUCCESS,
            actor(managerId),
            0,
            10
        );
        Page<AdmissionCheckInDtos.LogListResponse> checkInOnly = service.getEventAdmissionLogs(
            eventId,
            AdmissionAction.CHECK_IN,
            AdmissionResult.SUCCESS,
            actor(managerId),
            0,
            10
        );

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(all.getContent())
            .extracting(AdmissionCheckInDtos.LogListResponse::admissionLogId)
            .containsExactly(latestLogId, oldLogId);
        assertThat(checkInOnly.getTotalElements()).isEqualTo(1);
        assertThat(checkInOnly.getContent().getFirst().gateName()).isEqualTo("A Gate");
        assertThat(checkInOnly.getContent().getFirst().staffNickname()).isEqualTo("nick-log-staff");

        assertThatThrownBy(() -> service.getEventAdmissionLogs(
            eventId,
            null,
            null,
            actor(staffId),
            0,
            20
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void sameQrConcurrentCheckInSucceedsOnceAndWritesDuplicateOnce() throws Exception {
        Long staffId = insertMember("checkin-staff-concurrent");
        Long holderId = insertMember("checkin-holder-concurrent");
        Long organizationId = insertOrganization("checkin-org-concurrent");
        insertOrganizationMember(organizationId, staffId, "OWNER", "ACTIVE");
        Long eventId = insertEvent(organizationId, "checkin-event-concurrent", "PUBLISHED", 1);
        Long ticketId = insertIssuedAdmissionTicket(eventId, holderId, "qr-checkin-concurrent");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        try {
            Future<?> first = executor.submit(() -> checkInConcurrently(
                ready,
                start,
                successCount,
                duplicateCount,
                eventId,
                staffId
            ));
            Future<?> second = executor.submit(() -> checkInConcurrently(
                ready,
                start,
                successCount,
                duplicateCount,
                eventId,
                staffId
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
        assertThat(ticketStatus(ticketId)).isEqualTo("USED");
        assertThat(countLogs(ticketId, "CHECK_IN", "SUCCESS")).isEqualTo(1);
        assertThat(countLogs(ticketId, "CHECK_IN", "DUPLICATE")).isEqualTo(1);
    }

    private void checkInConcurrently(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successCount,
            AtomicInteger duplicateCount,
            Long eventId,
            Long staffId) {
        try {
            ready.countDown();
            start.await();
            service.checkIn(
                eventId,
                new AdmissionCheckInDtos.CheckInRequest("qr-checkin-concurrent", null),
                actor(staffId)
            );
            successCount.incrementAndGet();
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == GlobalErrorCode.ADMISSION_CHECK_IN_DUPLICATE) {
                duplicateCount.incrementAndGet();
                return;
            }
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private void insertEventMemberAllowedStaffFixture(Long eventId, Long staffId) {
        insertEventMember(eventId, staffId, "CHECKIN_STAFF", true);
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

    private Long insertOrganization(String suffix) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO organizations (
                organization_type, name, contact_email, contact_phone,
                status, created_at, updated_at
            )
            VALUES ('SOLE_PROPRIETOR', ?, ?, '01012345678',
                'ACTIVE', ?, ?)
            RETURNING id
            """,
            Long.class,
            "org-" + suffix,
            "org-" + suffix + "@example.com",
            now,
            now
        );
    }

    private void insertOrganizationMember(
            Long organizationId,
            Long memberId,
            String role,
            String status) {
        jdbcTemplate.update("""
            INSERT INTO organization_members (
                organization_id, member_id, organization_role, status, joined_at
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            organizationId,
            memberId,
            role,
            status,
            OffsetDateTime.now()
        );
    }

    private void insertEventMember(Long eventId, Long memberId, String role, boolean active) {
        jdbcTemplate.update("""
            INSERT INTO event_members (
                event_id, member_id, event_role, active, created_at
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            eventId,
            memberId,
            role,
            active,
            OffsetDateTime.now()
        );
    }

    private Long insertEvent(
            Long organizationId,
            String name,
            String status,
            int endAtOffsetDays) {
        OffsetDateTime now = OffsetDateTime.now();
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

    private Long insertIssuedAdmissionTicket(Long eventId, Long memberId, String qrToken) {
        return insertAdmissionTicket(eventId, memberId, qrToken, "ISSUED");
    }

    private Long insertUsedAdmissionTicket(Long eventId, Long memberId, String qrToken) {
        return insertAdmissionTicket(eventId, memberId, qrToken, "USED");
    }

    private Long insertAdmissionTicket(
            Long eventId,
            Long memberId,
            String qrToken,
            String status) {
        Long ticketOrderId = insertTicketOrder(eventId);
        Long exchangeCodeId = insertExchangeCode(eventId, ticketOrderId, memberId);
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO admission_tickets (
                exchange_code_id, member_id, qr_token, status, issued_at, used_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            exchangeCodeId,
            memberId,
            qrToken,
            status,
            now.minusMinutes(10),
            "USED".equals(status) ? now.minusMinutes(1) : null
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

    private Long insertExchangeCode(Long eventId, Long ticketOrderId, Long holderMemberId) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_codes (
                event_id, ticket_order_id, holder_member_id, code, status,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, 'REDEEMED', ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            ticketOrderId,
            holderMemberId,
            "930000-" + ticketOrderId + "-" + System.nanoTime(),
            now,
            now
        );
    }

    private Long insertAdmissionLog(
            Long admissionTicketId,
            Long staffMemberId,
            String action,
            String result,
            String gateName,
            OffsetDateTime processedAt) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO admission_logs (
                admission_ticket_id, staff_member_id, action, result, gate_name, processed_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            admissionTicketId,
            staffMemberId,
            action,
            result,
            gateName,
            processedAt
        );
    }

    private String ticketStatus(Long ticketId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM admission_tickets WHERE id = ?",
            String.class,
            ticketId
        );
    }

    private OffsetDateTime ticketUsedAt(Long ticketId) {
        return jdbcTemplate.queryForObject(
            "SELECT used_at FROM admission_tickets WHERE id = ?",
            OffsetDateTime.class,
            ticketId
        );
    }

    private OffsetDateTime ticketCancelledAt(Long ticketId) {
        return jdbcTemplate.queryForObject(
            "SELECT cancelled_at FROM admission_tickets WHERE id = ?",
            OffsetDateTime.class,
            ticketId
        );
    }

    private Integer countLogs(Long ticketId, String action, String result) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admission_logs WHERE admission_ticket_id = ? AND action = ? AND result = ?",
            Integer.class,
            ticketId,
            action,
            result
        );
    }

    private Integer countAllLogs(Long ticketId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admission_logs WHERE admission_ticket_id = ?",
            Integer.class,
            ticketId
        );
    }

    private String logGateName(Long ticketId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT gate_name
            FROM admission_logs
            WHERE admission_ticket_id = ?
                AND action = 'CHECK_IN'
                AND result = 'SUCCESS'
            """,
            String.class,
            ticketId
        );
    }
}
