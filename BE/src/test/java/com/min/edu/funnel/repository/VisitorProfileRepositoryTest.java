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

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.funnel.domain.VisitorProfile;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VisitorProfileRepositoryTest {

    @Autowired
    private VisitorProfileRepository visitorProfileRepository;

    @Test
    void save_thenFindByVisitorKey_returnsSavedProfile() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        VisitorProfile profile = VisitorProfile.create("anon:visitor-1", "visitor-1", null, now);

        visitorProfileRepository.save(profile);

        Optional<VisitorProfile> found = visitorProfileRepository.findByVisitorKey("anon:visitor-1");
        assertThat(found).isPresent();
        assertThat(found.get().getAnonymousId()).isEqualTo("visitor-1");
        assertThat(found.get().getUserId()).isNull();
        assertThat(found.get().getFirstSeenAt()).isEqualTo(now);
    }

    @Test
    void findByVisitorKey_unknownKey_returnsEmpty() {
        assertThat(visitorProfileRepository.findByVisitorKey("anon:unknown")).isEmpty();
    }

    @Test
    void save_duplicateVisitorKey_violatesUniqueConstraint() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        visitorProfileRepository.saveAndFlush(VisitorProfile.create("anon:dup", "dup", null, now));

        assertThatThrownBy(() ->
                visitorProfileRepository.saveAndFlush(VisitorProfile.create("anon:dup", "dup", null, now)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
