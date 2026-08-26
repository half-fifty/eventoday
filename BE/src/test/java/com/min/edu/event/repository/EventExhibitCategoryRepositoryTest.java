package com.min.edu.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.event.domain.EventExhibitCategory;
import com.min.edu.event.domain.EventExhibitCategoryId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventExhibitCategoryRepositoryTest {

    @Autowired
    private EventExhibitCategoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void replaceCategories_allowsKeepingOneExistingCompositeKey() {
        Long eventId = insertEvent();
        Long categoryA = categoryId("AGRI_FOOD");
        Long categoryB = categoryId("ENERGY_ENVIRONMENT");
        Long categoryC = categoryId("TEXTILE_FASHION_JEWELRY");
        repository.saveAllAndFlush(List.of(relation(eventId, categoryA), relation(eventId, categoryB)));

        repository.deleteAllByIdEventId(eventId);
        repository.flush();
        repository.saveAllAndFlush(List.of(relation(eventId, categoryB), relation(eventId, categoryC)));

        assertThat(repository.findCodesByEventId(eventId))
                .containsExactly("ENERGY_ENVIRONMENT", "TEXTILE_FASHION_JEWELRY");
    }

    private EventExhibitCategory relation(Long eventId, Long categoryId) {
        return new EventExhibitCategory(new EventExhibitCategoryId(eventId, categoryId));
    }

    private Long categoryId(String code) {
        return jdbcTemplate.queryForObject(
                "select id from exhibit_categories where code = ?", Long.class, code);
    }

    private Long insertEvent() {
        Long organizationId = jdbcTemplate.queryForObject("""
                insert into organizations (organization_type, name, contact_email, contact_phone,
                    status, created_at, updated_at)
                values ('COMPANY', '테스트 주최사', 'category-test@example.com', '02-0000-0000',
                    'ACTIVE', now(), now()) returning id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                insert into events (
                    organizer_organization_id, name, event_type, description, venue_name, address,
                    start_at, end_at, ticket_price, ticket_total_quantity, ticket_sold_quantity,
                    ticket_purchase_limit, status, booth_recruitment_enabled, venue_map_enabled,
                    booth_reservation_enabled, no_show_grace_minutes, created_at, updated_at
                ) values (?, '카테고리 교체 테스트', 'EXPO', '테스트', '테스트 전시장', '서울',
                    now(), now() + interval '1 day', 0, 100, 0, 1, 'PREPARING', false, false,
                    false, 0, now(), now()) returning id
                """, Long.class, organizationId);
    }
}
