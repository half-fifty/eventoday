package com.min.edu.notification.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void claim_pendingRowDueForRetry_succeedsAndMovesToProcessing() {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.create("42", "{}", now.minusSeconds(1)));

        int claimed = outboxEventRepository.claim(event.getId(), now, now.plusSeconds(30));

        assertThat(claimed).isEqualTo(1);
        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
    }

    @Test
    void claim_pendingRowNotYetDueForRetry_failsAndLeavesRowPending() {
        // 스레드풀 지연으로 뒤늦게 도착한 claim() 호출이, 그 사이 다른 작업이 설정해둔
        // 백오프(next_attempt_at이 미래)를 무시하고 재발행하지 않는지 확인한다.
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent event = outboxEventRepository.save(
            OutboxEvent.create("42", "{}", now.plusSeconds(10))
        );

        int claimed = outboxEventRepository.claim(event.getId(), now, now.plusSeconds(30));

        assertThat(claimed).isZero();
        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void claim_processingRowWithExpiredLease_succeeds() {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.create("42", "{}", now.minusSeconds(60)));
        outboxEventRepository.claim(event.getId(), now.minusSeconds(60), now.minusSeconds(30));

        int claimed = outboxEventRepository.claim(event.getId(), now, now.plusSeconds(30));

        assertThat(claimed).isEqualTo(1);
    }

    @Test
    void claim_processingRowWithActiveLease_fails() {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.create("42", "{}", now.minusSeconds(1)));
        outboxEventRepository.claim(event.getId(), now, now.plusSeconds(30));

        int claimed = outboxEventRepository.claim(event.getId(), now, now.plusSeconds(30));

        assertThat(claimed).isZero();
    }
}
