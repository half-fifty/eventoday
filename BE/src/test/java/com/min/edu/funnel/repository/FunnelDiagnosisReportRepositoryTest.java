package com.min.edu.funnel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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
import com.min.edu.funnel.domain.FunnelDiagnosisReport;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FunnelDiagnosisReportRepositoryTest {

    @Autowired
    private FunnelDiagnosisReportRepository funnelDiagnosisReportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void save_thenFindByEventIdAndReportDate_returnsSavedReport() {
        Long eventId = insertEvent();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T03:00:00+09:00");
        LocalDate reportDate = LocalDate.of(2026, 8, 17);

        FunnelDiagnosisReport report = FunnelDiagnosisReport.create(
                eventId,
                reportDate,
                "{\"VIEW_EVENT_DETAIL\":0.8,\"OPEN_PURCHASE_MODAL\":0.4}",
                true,
                true,
                "결제 선택 단계 이탈률이 평소보다 높습니다.",
                0.55,
                120,
                30,
                now);

        funnelDiagnosisReportRepository.save(report);

        Optional<FunnelDiagnosisReport> found =
                funnelDiagnosisReportRepository.findByEventIdAndReportDate(eventId, reportDate);
        assertThat(found).isPresent();
        assertThat(found.get().isAnomalyDetected()).isTrue();
        assertThat(found.get().getAiComment()).contains("이탈률");
        assertThat(found.get().getSampleSize()).isEqualTo(120);
    }

    @Test
    void save_duplicateEventAndDate_violatesUniqueConstraint() {
        Long eventId = insertEvent();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T03:00:00+09:00");
        LocalDate reportDate = LocalDate.of(2026, 8, 17);

        funnelDiagnosisReportRepository.saveAndFlush(FunnelDiagnosisReport.create(
                eventId, reportDate, "{}", false, false, null, null, 10, 30, now));

        assertThatThrownBy(() -> funnelDiagnosisReportRepository.saveAndFlush(FunnelDiagnosisReport.create(
                eventId, reportDate, "{}", false, false, null, null, 20, 30, now)))
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
