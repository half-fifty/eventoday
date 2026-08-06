package com.min.edu.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExchangeCodeRequestRepositoryTest {

    @Autowired
    private ExchangeCodeRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findEventRequests_returnsProjectionSortedByCreatedAtDescAndIdDesc() {
        Long memberId = insertMember("event-list");
        Long eventId = insertEvent("event-list");
        Long oldId = insertRequest(eventId, memberId, "APPROVED",
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00"));
        Long latestLowId = insertRequest(eventId, memberId, "REQUESTED",
            OffsetDateTime.parse("2026-08-06T11:00:00+09:00"));
        Long latestHighId = insertRequest(eventId, memberId, "REJECTED",
            OffsetDateTime.parse("2026-08-06T11:00:00+09:00"));

        Page<ExchangeCodeRequestView> page =
            repository.findEventRequests(eventId, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
            .extracting(ExchangeCodeRequestView::getRequestId)
            .containsExactly(latestHighId, latestLowId, oldId);
        assertThat(page.getContent().get(0).getEventName()).isEqualTo("event-list");
        assertThat(page.getContent().get(0).getRequesterNickname()).isEqualTo("nick-event-list");
    }

    @Test
    void findEventRequests_filtersByStatusAndCountMatchesContent() {
        Long memberId = insertMember("event-filter");
        Long eventId = insertEvent("event-filter");
        insertRequest(eventId, memberId, "REQUESTED", OffsetDateTime.now());
        insertRequest(eventId, memberId, "APPROVED", OffsetDateTime.now().minusMinutes(1));

        Page<ExchangeCodeRequestView> page = repository.findEventRequests(
            eventId,
            ExchangeCodeRequestStatus.APPROVED,
            PageRequest.of(0, 1)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus())
            .isEqualTo(ExchangeCodeRequestStatus.APPROVED);
    }

    @Test
    void findAdminRequests_filtersByStatusAndSorts() {
        Long memberId = insertMember("admin-list");
        Long eventOneId = insertEvent("admin-one");
        Long eventTwoId = insertEvent("admin-two");
        Long requestedOld = insertRequest(eventOneId, memberId, "REQUESTED",
            OffsetDateTime.parse("2030-08-06T10:00:00+09:00"));
        Long requestedNew = insertRequest(eventTwoId, memberId, "REQUESTED",
            OffsetDateTime.parse("2030-08-06T11:00:00+09:00"));
        insertRequest(eventOneId, memberId, "APPROVED",
            OffsetDateTime.parse("2030-08-06T12:00:00+09:00"));

        Page<ExchangeCodeRequestView> page = repository.findAdminRequests(
            ExchangeCodeRequestStatus.REQUESTED,
            PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent())
            .extracting(ExchangeCodeRequestView::getRequestId)
            .startsWith(requestedNew, requestedOld);
    }

    @Test
    void findRequestDetail_returnsJoinedEventAndRequester() {
        Long memberId = insertMember("detail");
        Long eventId = insertEvent("detail-event");
        Long requestId = insertRequest(eventId, memberId, "REQUESTED", OffsetDateTime.now());

        ExchangeCodeRequestView detail = repository.findRequestDetail(requestId).orElseThrow();

        assertThat(detail.getRequestId()).isEqualTo(requestId);
        assertThat(detail.getEventName()).isEqualTo("detail-event");
        assertThat(detail.getRequesterNickname()).isEqualTo("nick-detail");
    }

    @Test
    void partialUniqueIndex_allowsOnlyOneRequestedRequestPerEvent() {
        Long memberId = insertMember("unique");
        Long eventId = insertEvent("unique-event");
        insertRequest(eventId, memberId, "REQUESTED", OffsetDateTime.now());

        assertThatThrownBy(() -> insertRequest(eventId, memberId, "REQUESTED", OffsetDateTime.now()))
            .isInstanceOf(DataIntegrityViolationException.class);
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

    private Long insertRequest(
            Long eventId,
            Long memberId,
            String status,
            OffsetDateTime createdAt) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_code_requests (
                event_id, requested_by, requested_quantity, purpose,
                status, created_at
            )
            VALUES (?, ?, 3, 'purpose', ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            memberId,
            status,
            createdAt
        );
    }
}
