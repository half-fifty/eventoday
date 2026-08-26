package com.min.edu.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.dto.AdmissionTicketView;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdmissionTicketRepositoryTest {

    @Autowired
    private AdmissionTicketRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findMyAdmissionTickets_filtersByMemberStatusAndSorts() {
        Long memberId = insertMember("my-admission");
        Long otherMemberId = insertMember("my-admission-other");
        Long eventId = insertEvent("my-admission-event");
        Long otherEventId = insertEvent("my-admission-other-event");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long otherTicketOrderId = insertTicketOrder(otherEventId);
        OffsetDateTime old = OffsetDateTime.now().plusHours(1);
        OffsetDateTime latest = old.plusMinutes(1);
        Long oldTicketId = insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, memberId, "920001-920001-920001"),
            memberId,
            "ISSUED",
            "qr-my-old",
            old
        );
        Long latestLowId = insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, memberId, "920002-920002-920002"),
            memberId,
            "USED",
            "qr-my-latest-low",
            latest
        );
        Long latestHighId = insertAdmissionTicket(
            insertExchangeCode(otherEventId, otherTicketOrderId, memberId, "920003-920003-920003"),
            memberId,
            "USED",
            "qr-my-latest-high",
            latest
        );
        insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, otherMemberId, "920004-920004-920004"),
            otherMemberId,
            "USED",
            "qr-other",
            latest.plusMinutes(1)
        );
        insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, null, "920005-920005-920005"),
            null,
            "ISSUED",
            "qr-null-member",
            latest.plusMinutes(2)
        );

        Page<AdmissionTicketView> all = repository.findMyAdmissionTickets(
            memberId,
            null,
            PageRequest.of(0, 10)
        );
        Page<AdmissionTicketView> used = repository.findMyAdmissionTickets(
            memberId,
            AdmissionTicketStatus.USED,
            PageRequest.of(0, 1)
        );

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent())
            .extracting(AdmissionTicketView::getAdmissionTicketId)
            .containsExactly(latestHighId, latestLowId, oldTicketId);
        assertThat(used.getTotalElements()).isEqualTo(2);
        assertThat(used.getContent()).hasSize(1);
        assertThat(used.getContent().getFirst().getStatus()).isEqualTo(AdmissionTicketStatus.USED);
    }

    @Test
    void findAdmissionTicketDetail_returnsJoinedEventAndExchangeCodeStatus() {
        Long memberId = insertMember("admission-detail");
        Long eventId = insertEvent("admission-detail-event");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long exchangeCodeId = insertExchangeCode(
            eventId,
            ticketOrderId,
            memberId,
            "920006-920006-920006"
        );
        Long admissionTicketId = insertAdmissionTicket(
            exchangeCodeId,
            memberId,
            "ISSUED",
            "qr-detail",
            OffsetDateTime.now()
        );

        AdmissionTicketView detail =
            repository.findAdmissionTicketDetail(admissionTicketId).orElseThrow();

        assertThat(detail.getAdmissionTicketId()).isEqualTo(admissionTicketId);
        assertThat(detail.getEventId()).isEqualTo(eventId);
        assertThat(detail.getEventName()).isEqualTo("admission-detail-event");
        assertThat(detail.getMemberId()).isEqualTo(memberId);
        assertThat(detail.getMemberNickname()).isEqualTo("nick-admission-detail");
        assertThat(detail.getExchangeCodeStatus().name()).isEqualTo("ISSUED");
        assertThat(detail.getQrToken()).isEqualTo("qr-detail");
    }

    @Test
    void findEventAdmissionTickets_filtersByEventStatusAndKeepsNullMember() {
        Long memberId = insertMember("event-admission");
        Long eventId = insertEvent("event-admission-event");
        Long otherEventId = insertEvent("event-admission-other-event");
        Long ticketOrderId = insertTicketOrder(eventId);
        Long otherTicketOrderId = insertTicketOrder(otherEventId);
        OffsetDateTime old = OffsetDateTime.now().plusHours(1);
        OffsetDateTime latest = old.plusMinutes(1);
        Long oldTicketId = insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, memberId, "920007-920007-920007"),
            memberId,
            "ISSUED",
            "qr-event-old",
            old
        );
        Long latestTicketId = insertAdmissionTicket(
            insertExchangeCode(eventId, ticketOrderId, null, "920008-920008-920008"),
            null,
            "CANCELLED",
            "qr-event-latest",
            latest
        );
        insertAdmissionTicket(
            insertExchangeCode(otherEventId, otherTicketOrderId, memberId, "920009-920009-920009"),
            memberId,
            "CANCELLED",
            "qr-event-other",
            latest.plusMinutes(1)
        );

        Page<AdmissionTicketView> all = repository.findEventAdmissionTickets(
            eventId,
            null,
            PageRequest.of(0, 10)
        );
        Page<AdmissionTicketView> cancelled = repository.findEventAdmissionTickets(
            eventId,
            AdmissionTicketStatus.CANCELLED,
            PageRequest.of(0, 1)
        );

        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(all.getContent())
            .extracting(AdmissionTicketView::getAdmissionTicketId)
            .containsExactly(latestTicketId, oldTicketId);
        assertThat(all.getContent().getFirst().getMemberNickname()).isNull();
        assertThat(cancelled.getTotalElements()).isEqualTo(1);
        assertThat(cancelled.getContent().getFirst().getAdmissionTicketId())
            .isEqualTo(latestTicketId);
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

    private Long insertExchangeCode(
            Long eventId,
            Long ticketOrderId,
            Long holderMemberId,
            String code) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO exchange_codes (
                event_id, ticket_order_id, holder_member_id, code, status,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, 'ISSUED', ?, ?)
            RETURNING id
            """,
            Long.class,
            eventId,
            ticketOrderId,
            holderMemberId,
            code,
            now,
            now
        );
    }

    private Long insertAdmissionTicket(
            Long exchangeCodeId,
            Long memberId,
            String status,
            String qrToken,
            OffsetDateTime issuedAt) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO admission_tickets (
                exchange_code_id, member_id, qr_token, status, issued_at
            )
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            exchangeCodeId,
            memberId,
            qrToken,
            status,
            issuedAt
        );
    }
}
