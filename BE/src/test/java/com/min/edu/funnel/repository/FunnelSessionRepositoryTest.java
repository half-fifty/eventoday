package com.min.edu.funnel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.funnel.domain.FunnelSession;
import com.min.edu.funnel.domain.FunnelStep;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FunnelSessionRepositoryTest {

    @Autowired
    private FunnelSessionRepository funnelSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void save_thenFindBySessionId_returnsSavedSession() {
        Long eventId = insertEvent();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        FunnelSession session = FunnelSession.create(
                "b9d8a554-940f-4d72-b6de-711616158aad",
                eventId,
                "anon:visitor-1",
                FunnelStep.OPEN_PURCHASE_MODAL,
                true,
                false,
                true,
                false,
                now,
                now.plusMinutes(5),
                now);

        funnelSessionRepository.save(session);

        Optional<FunnelSession> found =
                funnelSessionRepository.findBySessionId("b9d8a554-940f-4d72-b6de-711616158aad");
        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(eventId);
        assertThat(found.get().getMaxStepReached()).isEqualTo(FunnelStep.OPEN_PURCHASE_MODAL);
        assertThat(found.get().isDropped()).isTrue();
        assertThat(found.get().isBoothExplored()).isTrue();
    }

    @Test
    void save_duplicateSessionId_violatesUniqueConstraint() {
        Long eventId = insertEvent();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");

        funnelSessionRepository.saveAndFlush(FunnelSession.create(
                "dup-session", eventId, "anon:v1", FunnelStep.VISIT, false, false, false, false, now, now, now));

        assertThatThrownBy(() -> funnelSessionRepository.saveAndFlush(FunnelSession.create(
                "dup-session", eventId, "anon:v2", FunnelStep.VISIT, false, false, false, false, now, now, now)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertEvent() {
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
                        + "now(), now() + interval '2 day', 10000, 100, 0, 4, 'PUBLISHED', false, false, false, 30, "
                        + "now(), now()) "
                        + "RETURNING id",
                Long.class,
                organizationId);
    }
}
