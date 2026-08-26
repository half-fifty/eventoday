package com.min.edu.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.event.domain.EventStatus;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findIdsForFunnelReconstruction_includesPublishedEvent() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");
        Long eventId = insertEvent(EventStatus.PUBLISHED, dayStart.plusDays(10));

        assertThat(eventRepository.findIdsForFunnelReconstruction(
                EventStatus.PUBLISHED, EventStatus.ENDED, dayStart))
            .contains(eventId);
    }

    @Test
    void findIdsForFunnelReconstruction_includesEventEndedOnTargetDate() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");
        // 대상 날짜(8/21) 안에 종료된 행사 — 배치 실행 시점(8/22 새벽)엔 이미 ENDED로 바뀌어 있다.
        Long eventId = insertEvent(EventStatus.ENDED, dayStart.plusHours(18));

        assertThat(eventRepository.findIdsForFunnelReconstruction(
                EventStatus.PUBLISHED, EventStatus.ENDED, dayStart))
            .contains(eventId);
    }

    @Test
    void findIdsForFunnelReconstruction_excludesEventEndedBeforeTargetDate() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");
        Long eventId = insertEvent(EventStatus.ENDED, dayStart.minusDays(1));

        assertThat(eventRepository.findIdsForFunnelReconstruction(
                EventStatus.PUBLISHED, EventStatus.ENDED, dayStart))
            .doesNotContain(eventId);
    }

    @Test
    void findIdsForFunnelReconstruction_excludesCancelledEvent() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");
        Long eventId = insertEvent(EventStatus.CANCELLED, dayStart.plusHours(1));

        assertThat(eventRepository.findIdsForFunnelReconstruction(
                EventStatus.PUBLISHED, EventStatus.ENDED, dayStart))
            .doesNotContain(eventId);
    }

    private Long insertEvent(EventStatus status, OffsetDateTime endAt) {
        Long organizationId = jdbcTemplate.queryForObject(
                "INSERT INTO organizations "
                        + "(organization_type, name, contact_email, contact_phone, status, created_at, updated_at) "
                        + "VALUES ('ORGANIZER', '테스트기획사', 'org@example.com', '010-0000-0000', 'ACTIVE', now(), now()) "
                        + "RETURNING id",
                Long.class);

        return jdbcTemplate.queryForObject(
                "INSERT INTO events "
                        + "(organizer_organization_id, name, event_type, description, venue_name, address, "
                        + "start_at, end_at, ticket_price, ticket_total_quantity, ticket_sold_quantity, "
                        + "ticket_purchase_limit, status, booth_recruitment_enabled, venue_map_enabled, "
                        + "booth_reservation_enabled, no_show_grace_minutes, created_at, updated_at) "
                        + "VALUES (?, '테스트 박람회', 'EXHIBITION', '설명', '테스트홀', '서울시 어딘가', "
                        + "?, ?, 10000, 100, 0, 4, ?, false, false, false, 30, now(), now()) "
                        + "RETURNING id",
                Long.class,
                organizationId,
                endAt.minusDays(2),
                endAt,
                status.name());
    }
}
