package com.min.edu.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import com.min.edu.admission.dto.ExchangeCodeView;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
        OffsetDateTime firstExpiresAt = OffsetDateTime.now().plusDays(10).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime secondExpiresAt = firstExpiresAt.plusDays(1);
        Long firstId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "AAAAAA-AAAAAA-AAAAAA",
            firstExpiresAt
        );
        Long secondId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "BBBBBB-BBBBBB-BBBBBB",
            secondExpiresAt
        );

        assertThat(exchangeCodeRepository.countByExchangeCodeRequestId(requestId)).isEqualTo(2);
        assertThat(exchangeCodeRepository.findAllByExchangeCodeRequestIdOrderByIdAsc(requestId))
            .extracting(com.min.edu.admission.domain.ExchangeCode::getId)
            .containsExactly(firstId, secondId);
        assertThat(exchangeCodeRepository.findAllByExchangeCodeRequestIdOrderByIdAsc(requestId))
            .extracting(code -> code.getExpiresAt().toInstant())
            .containsExactly(firstExpiresAt.toInstant(), secondExpiresAt.toInstant());
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

    @Test
    void findEventExchangeCodes_returnsBothSourcesWithHolderNullableAndSorted() {
        Long holderId = insertMember("event-code-holder");
        Long requesterId = insertMember("event-code-requester");
        Long eventId = insertEvent("event-code-list");
        Long otherEventId = insertEvent("event-code-list-other");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long otherTicketOrderId = insertTicketOrder(otherEventId);
        Long requestId = insertRequest(eventId, requesterId, "ISSUED", OffsetDateTime.now());
        OffsetDateTime old = OffsetDateTime.now().plusHours(1);
        OffsetDateTime latest = old.plusMinutes(1);
        Long oldTicketCodeId = insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900001-900001-900001",
            "ISSUED",
            old
        );
        Long latestLowId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "900002-900002-900002",
            "REDEEMED",
            latest,
            null
        );
        Long latestHighId = insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900003-900003-900003",
            "ISSUED",
            latest
        );
        Long otherEventMyCodeId = insertExchangeCodeForTicketOrder(
            otherEventId,
            otherTicketOrderId,
            holderId,
            "900012-900012-900012",
            "ISSUED",
            latest.plusMinutes(1)
        );

        Page<ExchangeCodeView> page = exchangeCodeRepository.findEventExchangeCodes(
            eventId,
            null,
            PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
            .extracting(ExchangeCodeView::getExchangeCodeId)
            .containsExactly(latestHighId, latestLowId, oldTicketCodeId);
        assertThat(page.getContent().get(0).getEventName()).isEqualTo("event-code-list");
        assertThat(page.getContent().get(0).getHolderNickname()).isEqualTo("nick-event-code-holder");
        assertThat(page.getContent().get(1).getHolderNickname()).isNull();
        assertThat(page.getContent().get(1).getExchangeCodeRequestId()).isEqualTo(requestId);
    }

    @Test
    void findEventExchangeCodes_filtersByStatusAndCountMatchesContent() {
        Long holderId = insertMember("event-code-filter-holder");
        Long requesterId = insertMember("event-code-filter-requester");
        Long eventId = insertEvent("event-code-filter");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long requestId = insertRequest(eventId, requesterId, "ISSUED", OffsetDateTime.now());
        insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900004-900004-900004",
            "ISSUED",
            OffsetDateTime.now()
        );
        insertExchangeCodeForRequest(
            eventId,
            requestId,
            "900005-900005-900005",
            "REDEEMED",
            OffsetDateTime.now().plusMinutes(1),
            null
        );

        Page<ExchangeCodeView> page = exchangeCodeRepository.findEventExchangeCodes(
            eventId,
            ExchangeCodeStatus.REDEEMED,
            PageRequest.of(0, 1)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
    }

    @Test
    void findMyExchangeCodes_returnsOnlyCurrentHolderAndExcludesNullHolder() {
        Long holderId = insertMember("my-code-holder");
        Long otherHolderId = insertMember("my-code-other");
        Long requesterId = insertMember("my-code-requester");
        Long eventId = insertEvent("my-code-event");
        Long otherEventId = insertEvent("my-code-other-event");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long otherTicketOrderId = insertTicketOrder(otherEventId);
        Long requestId = insertRequest(eventId, requesterId, "ISSUED", OffsetDateTime.now());
        Long myTicketCodeId = insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900006-900006-900006",
            "ISSUED",
            OffsetDateTime.now().plusMinutes(1)
        );
        Long myExternalCodeId = insertExchangeCodeForRequest(
            eventId,
            requestId,
            "900007-900007-900007",
            "CANCELLED",
            OffsetDateTime.now().plusMinutes(2),
            holderId
        );
        insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            otherHolderId,
            "900008-900008-900008",
            "ISSUED",
            OffsetDateTime.now().plusMinutes(3)
        );
        insertExchangeCodeForRequest(
            eventId,
            requestId,
            "900009-900009-900009",
            "ISSUED",
            OffsetDateTime.now().plusMinutes(4),
            null
        );
        Long otherEventMyCodeId = insertExchangeCodeForTicketOrder(
            otherEventId,
            otherTicketOrderId,
            holderId,
            "900013-900013-900013",
            "REDEEMED",
            OffsetDateTime.now().plusMinutes(5)
        );

        Page<ExchangeCodeView> page = exchangeCodeRepository.findMyExchangeCodes(
            holderId,
            null,
            PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
            .extracting(ExchangeCodeView::getExchangeCodeId)
            .containsExactly(otherEventMyCodeId, myExternalCodeId, myTicketCodeId);
        assertThat(page.getContent())
            .extracting(ExchangeCodeView::getCode)
            .containsExactly(
                "900013-900013-900013",
                "900007-900007-900007",
                "900006-900006-900006"
            );
    }

    @Test
    void findMyExchangeCodes_filtersByStatusAndPaginates() {
        Long holderId = insertMember("my-code-filter-holder");
        Long eventId = insertEvent("my-code-filter");
        Long ticketOrderId = insertTicketOrder(eventId);
        insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900010-900010-900010",
            "ISSUED",
            OffsetDateTime.now()
        );
        insertExchangeCodeForTicketOrder(
            eventId,
            ticketOrderId,
            holderId,
            "900011-900011-900011",
            "EXPIRED",
            OffsetDateTime.now().plusMinutes(1)
        );

        Page<ExchangeCodeView> page = exchangeCodeRepository.findMyExchangeCodes(
            holderId,
            ExchangeCodeStatus.EXPIRED,
            PageRequest.of(0, 1)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(ExchangeCodeStatus.EXPIRED);
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
        return insertExchangeCodeForRequest(
            eventId,
            requestId,
            code,
            "ISSUED",
            OffsetDateTime.now(),
            expiresAt,
            null
        );
    }

    private Long insertExchangeCodeForRequest(
            Long eventId,
            Long requestId,
            String code,
            String status,
            OffsetDateTime createdAt,
            Long holderMemberId) {
        return insertExchangeCodeForRequest(
            eventId,
            requestId,
            code,
            status,
            createdAt,
            createdAt.plusDays(1),
            holderMemberId
        );
    }

    private Long insertExchangeCodeForRequest(
            Long eventId,
            Long requestId,
            String code,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            Long holderMemberId) {
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
            createdAt,
            now
        );
    }

    private Long insertExchangeCodeForTicketOrder(
            Long eventId,
            Long ticketOrderId,
            Long holderMemberId,
            String code,
            String status,
            OffsetDateTime createdAt) {
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
            createdAt,
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
