package com.min.edu.payment.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.service.PaymentOutboxLeaseHeartbeat;
import com.min.edu.payment.outbox.service.PaymentOutboxLeaseService;
import com.min.edu.payment.outbox.service.PaymentOutboxResultService;

import jakarta.persistence.EntityManager;

@Import({
    TestcontainersConfiguration.class,
    PaymentOutboxLeaseService.class,
    PaymentOutboxLeaseHeartbeat.class,
    PaymentOutboxProperties.class
})
@DataJpaTest(properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentOutboxEventRepositoryTest {

    @Autowired
    private PaymentOutboxEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PaymentOutboxLeaseService leaseService;

    @Autowired
    private PaymentOutboxLeaseHeartbeat leaseHeartbeat;

    @Autowired
    private PaymentOutboxProperties properties;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

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
        PaymentOutboxEvent published = repository.save(event("ORDER-3", now.minusSeconds(1)));
        PaymentOutboxEvent failed = repository.save(event("ORDER-4", now.minusSeconds(1)));
        repository.claim(processing.getId(), "worker-1", now.minusSeconds(60), now.minusSeconds(30));
        repository.findById(published.getId()).orElseThrow().markPublished(now);
        repository.findById(failed.getId()).orElseThrow().markFailed("failure");
        flushAndClear();

        assertThat(repository.findClaimableIds(now, PageRequest.of(0, 10)))
            .containsExactly(pending.getId(), processing.getId());
    }

    @Test
    void renewLease_currentOwnerExtendsLeaseAndPreventsReclaimAtOriginalExpiry() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(1)));
        repository.claim(event.getId(), "worker-a", now, now.plusSeconds(1));

        boolean renewed = leaseService.renew(event.getId(), "worker-a");
        int claimedByOtherWorker = repository.claim(
            event.getId(),
            "worker-b",
            now.plusSeconds(2),
            now.plusSeconds(30)
        );

        assertThat(renewed).isTrue();
        assertThat(claimedByOtherWorker).isZero();
        PaymentOutboxEvent reloaded = repository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getLeaseOwner()).isEqualTo("worker-a");
        assertThat(reloaded.getLeaseUntil()).isAfter(now.plusSeconds(2));
    }

    @Test
    void renewLease_staleOwnerReturnsFalseAndResultUpdateCannotModifyRow() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(1)));
        repository.claim(event.getId(), "worker-a", now.minusSeconds(60), now.minusSeconds(30));
        repository.claim(event.getId(), "worker-b", now, now.plusSeconds(30));
        flushAndClear();
        PaymentOutboxResultService resultService =
            new PaymentOutboxResultService(repository, new PaymentOutboxProperties());

        boolean renewed = leaseService.renew(event.getId(), "worker-a");
        boolean published = resultService.markPublished(event.getId(), "worker-a");

        assertThat(renewed).isFalse();
        assertThat(published).isFalse();
        PaymentOutboxEvent reloaded = repository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentOutboxEventStatus.PROCESSING);
        assertThat(reloaded.getLeaseOwner()).isEqualTo("worker-b");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void heartbeatDuringBlockingSendPreventsReclaimUntilStopped_thenExpiredLeaseCanBeReclaimed() throws Exception {
        Duration originalLeaseDuration = properties.getLeaseDuration();
        Duration originalRenewalInterval = properties.getLeaseRenewalInterval();
        properties.setLeaseDuration(Duration.ofMillis(250));
        properties.setLeaseRenewalInterval(Duration.ofMillis(50));
        try {
            OffsetDateTime now = OffsetDateTime.now();
            PaymentOutboxEvent event = repository.save(event("ORDER-1", now.minusSeconds(1)));
            repository.claim(event.getId(), "worker-a", now, now.plus(Duration.ofMillis(80)));

            try (PaymentOutboxLeaseHeartbeat.LeaseHeartbeat ignored =
                     leaseHeartbeat.start(event.getId(), "worker-a")) {
                TimeUnit.MILLISECONDS.sleep(180);

                int claimedWhileHeartbeatRuns = repository.claim(
                    event.getId(),
                    "worker-b",
                    OffsetDateTime.now(),
                    OffsetDateTime.now().plusSeconds(1)
                );

                assertThat(claimedWhileHeartbeatRuns).isZero();
                assertThat(repository.findById(event.getId()).orElseThrow().getLeaseOwner())
                    .isEqualTo("worker-a");
            }

            TimeUnit.MILLISECONDS.sleep(350);
            int claimedAfterHeartbeatStopped = repository.claim(
                event.getId(),
                "worker-b",
                OffsetDateTime.now(),
                OffsetDateTime.now().plusSeconds(1)
            );

            assertThat(claimedAfterHeartbeatStopped).isEqualTo(1);
            assertThat(repository.findById(event.getId()).orElseThrow().getLeaseOwner())
                .isEqualTo("worker-b");
        } finally {
            properties.setLeaseDuration(originalLeaseDuration);
            properties.setLeaseRenewalInterval(originalRenewalInterval);
        }
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
