package com.min.edu.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import com.min.edu.admission.repository.ExchangeCodeRepository;
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
    private ExchangeCodeRepository exchangeCodeRepository;

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
        OffsetDateTime base = OffsetDateTime.now().plusHours(1);
        Long requestedOld = insertRequest(eventOneId, memberId, "REQUESTED", base);
        Long requestedNew = insertRequest(eventTwoId, memberId, "REQUESTED", base.plusMinutes(1));
        insertRequest(eventOneId, memberId, "APPROVED", base.plusMinutes(2));

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

    @Test
    void exchangeCodesForRequest_passXorCheckAndCanBeCountedAndListed() {
        Long memberId = insertMember("external-code");
        Long eventId = insertEvent("external-code-event");
        Long requestId = insertRequest(eventId, memberId, "ISSUED", OffsetDateTime.now());
        Long firstId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "AAAAAA-AAAAAA-AAAAAA",
            OffsetDateTime.now().plusDays(1)
        );
        Long secondId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "BBBBBB-BBBBBB-BBBBBB",
            OffsetDateTime.now().plusDays(1)
        );

        assertThat(exchangeCodeRepository.countByExchangeCodeRequestId(requestId)).isEqualTo(2);
        assertThat(exchangeCodeRepository.findAllByExchangeCodeRequestIdOrderByIdAsc(requestId))
            .extracting(com.min.edu.admission.domain.ExchangeCode::getId)
            .containsExactly(firstId, secondId);
    }

    @Test
    void exchangeCodesXorCheck_failsWhenBothSourcesAreNull() {
        Long eventId = insertEvent("xor-null");

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO exchange_codes (
                event_id, code, status, created_at, updated_at
            )
            VALUES (?, 'CCCCCC-CCCCCC-CCCCCC', 'ISSUED', ?, ?)
            """,
            eventId,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exchangeCodesXorCheck_failsWhenBothSourcesArePresent() {
        Long memberId = insertMember("xor-both");
        Long eventId = insertEvent("xor-both-event");
        Long requestId = insertRequest(eventId, memberId, "ISSUED", OffsetDateTime.now());
        Long ticketOrderId = insertTicketOrder(eventId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO exchange_codes (
                event_id, exchange_code_request_id, ticket_order_id,
                code, status, created_at, updated_at
            )
            VALUES (?, ?, ?, 'DDDDDD-DDDDDD-DDDDDD', 'ISSUED', ?, ?)
            """,
            eventId,
            requestId,
            ticketOrderId,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exchangeCodesCodeUniqueConstraint_rejectsDuplicateCode() {
        Long memberId = insertMember("code-unique");
        Long eventId = insertEvent("code-unique-event");
        Long requestId = insertRequest(eventId, memberId, "ISSUED", OffsetDateTime.now());
        insertExchangeCodeForRequest(
            eventId,
            requestId,
            "EEEEEE-EEEEEE-EEEEEE",
            OffsetDateTime.now().plusDays(1)
        );

        assertThatThrownBy(() -> insertExchangeCodeForRequest(
            eventId,
            requestId,
            "EEEEEE-EEEEEE-EEEEEE",
            OffsetDateTime.now().plusDays(1)
        )).isInstanceOf(DataIntegrityViolationException.class);
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

    private Long insertExchangeCodeForRequest(
            Long eventId,
            Long requestId,
            String code,
            OffsetDateTime expiresAt) {
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
            expiresAt,
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
}
