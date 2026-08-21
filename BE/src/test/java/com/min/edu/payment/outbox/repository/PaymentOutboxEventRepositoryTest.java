package com.min.edu.payment.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.service.PaymentOutboxResultService;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@DataJpaTest(properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentOutboxEventRepositoryTest {

    @Autowired
    private PaymentOutboxEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void claim_pendingRow_succeedsAndStoresOwnerLease() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(1)));

        int claimed = repository.claim(event.getId(), "worker-1", now, now.plusSeconds(30));

        assertThat(claimed).isEqualTo(1);
        PaymentOutboxEvent reloaded = repository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentOutboxEventStatus.PROCESSING);
        assertThat(reloaded.getLeaseOwner()).isEqualTo("worker-1");
        assertThat(reloaded.getLeaseUntil()).isAfter(now.plusSeconds(29));
        assertThat(reloaded.getLeaseUntil()).isBefore(now.plusSeconds(31));
    }

    @Test
    void claim_pendingRowBeforeBackoff_fails() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.plusSeconds(10)));

        int claimed = repository.claim(event.getId(), "worker-1", now, now.plusSeconds(30));

        assertThat(claimed).isZero();
        assertThat(repository.findClaimableIds(now, PageRequest.of(0, 10))).doesNotContain(event.getId());
        assertThat(repository.findById(event.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentOutboxEventStatus.PENDING);
    }

    @Test
    void concurrentClaim_onlyOneOwnerWins() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(1)));

        int first = repository.claim(event.getId(), "worker-1", now, now.plusSeconds(30));
        int second = repository.claim(event.getId(), "worker-2", now, now.plusSeconds(30));

        assertThat(first + second).isEqualTo(1);
        assertThat(repository.findById(event.getId()).orElseThrow().getLeaseOwner())
            .isEqualTo("worker-1");
    }

    @Test
    void expiredProcessingLease_canBeReclaimed() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(60)));
        repository.claim(event.getId(), "worker-1", now.minusSeconds(60), now.minusSeconds(30));

        int reclaimed = repository.claim(event.getId(), "worker-2", now, now.plusSeconds(30));

        assertThat(reclaimed).isEqualTo(1);
        assertThat(repository.findById(event.getId()).orElseThrow().getLeaseOwner())
            .isEqualTo("worker-2");
    }

    @Test
    void staleOwnerSuccessCannotUpdateReclaimedRow() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(60)));
        repository.claim(event.getId(), "worker-a", now.minusSeconds(60), now.minusSeconds(30));
        repository.claim(event.getId(), "worker-b", now, now.plusSeconds(30));
        flushAndClear();
        PaymentOutboxResultService resultService =
            new PaymentOutboxResultService(repository, new PaymentOutboxProperties());

        boolean staleUpdated = resultService.markPublished(event.getId(), "worker-a");
        PaymentOutboxEvent afterStale = repository.findById(event.getId()).orElseThrow();

        assertThat(staleUpdated).isFalse();
        assertThat(afterStale.getStatus()).isEqualTo(PaymentOutboxEventStatus.PROCESSING);
        assertThat(afterStale.getLeaseOwner()).isEqualTo("worker-b");
        boolean currentUpdated = resultService.markPublished(event.getId(), "worker-b");
        assertThat(currentUpdated).isTrue();
        assertThat(repository.findById(event.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentOutboxEventStatus.PUBLISHED);
    }

    @Test
    void staleOwnerFailureCannotUpdateReclaimedRow() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(60)));
        repository.claim(event.getId(), "worker-a", now.minusSeconds(60), now.minusSeconds(30));
        repository.claim(event.getId(), "worker-b", now, now.plusSeconds(30));
        flushAndClear();
        PaymentOutboxResultService resultService =
            new PaymentOutboxResultService(repository, new PaymentOutboxProperties());

        boolean staleUpdated = resultService.markSendFailure(
            event.getId(),
            "worker-a",
            new IllegalStateException("late failure")
        );
        PaymentOutboxEvent afterStale = repository.findById(event.getId()).orElseThrow();

        assertThat(staleUpdated).isFalse();
        assertThat(afterStale.getStatus()).isEqualTo(PaymentOutboxEventStatus.PROCESSING);
        assertThat(afterStale.getRetryCount()).isZero();
        assertThat(afterStale.getLeaseOwner()).isEqualTo("worker-b");
        boolean currentUpdated = resultService.markSendFailure(
            event.getId(),
            "worker-b",
            new IllegalStateException("current failure")
        );
        assertThat(currentUpdated).isTrue();
        PaymentOutboxEvent afterCurrent = repository.findById(event.getId()).orElseThrow();
        assertThat(afterCurrent.getStatus()).isEqualTo(PaymentOutboxEventStatus.PENDING);
        assertThat(afterCurrent.getRetryCount()).isEqualTo(1);
    }

    @Test
    void findClaimableIds_excludesPublishedAndFailedRows() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent pending = repository.save(event("ORDER-1", now.minusSeconds(1)));
        PaymentOutboxEvent processing = repository.save(event("ORDER-2", now.minusSeconds(1)));
        repository.claim(processing.getId(), "worker-1", now.minusSeconds(60), now.minusSeconds(30));

        assertThat(repository.findClaimableIds(now, PageRequest.of(0, 10)))
            .containsExactly(pending.getId(), processing.getId());
    }

    private PaymentOutboxEvent event(String orderNo, OffsetDateTime availableAt) {
        return PaymentOutboxEvent.ticketReservationConfirmation(
            orderNo,
            "{\"orderNo\":\"" + orderNo + "\",\"buyerEmail\":\"guest@example.com\",\"eventName\":\"event\"}",
            availableAt
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
