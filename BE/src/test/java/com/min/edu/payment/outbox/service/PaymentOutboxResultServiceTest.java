package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;

class PaymentOutboxResultServiceTest {

    private final PaymentOutboxEventRepository repository =
        Mockito.mock(PaymentOutboxEventRepository.class);
    private final PaymentOutboxProperties properties = properties();
    private final PaymentOutboxResultService service =
        new PaymentOutboxResultService(repository, properties);

    @Test
    void markPublished_currentOwnerPublishes() {
        PaymentOutboxEvent event = processingEvent();
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "owner-1"
        )).willReturn(Optional.of(event));

        boolean updated = service.markPublished(1L, "owner-1");

        assertThat(updated).isTrue();
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void markPublished_staleOwnerCannotUpdate() {
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "stale-owner"
        )).willReturn(Optional.empty());

        assertThat(service.markPublished(1L, "stale-owner")).isFalse();
    }

    @Test
    void markSendFailure_currentOwnerRetriesWithBackoff() {
        PaymentOutboxEvent event = processingEvent();
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "owner-1"
        )).willReturn(Optional.of(event));

        boolean updated = service.markSendFailure(1L, "owner-1", new IllegalStateException("smtp down"));

        assertThat(updated).isTrue();
        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getAvailableAt()).isAfter(OffsetDateTime.now());
        assertThat(event.getLastError()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getLastError()).doesNotContain("smtp down");
    }

    @Test
    void markSendFailure_capsBackoffAtMaxBackoff() {
        PaymentOutboxEvent event = processingEvent(4);
        properties.setMaxBackoff(Duration.ofSeconds(6));
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "owner-1"
        )).willReturn(Optional.of(event));
        OffsetDateTime before = OffsetDateTime.now();

        service.markSendFailure(1L, "owner-1", new IllegalStateException("smtp down"));

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getAvailableAt()).isBeforeOrEqualTo(before.plusSeconds(7));
        assertThat(event.getAvailableAt()).isAfterOrEqualTo(before.plusSeconds(5));
    }

    @Test
    void markSendFailure_doesNotPersistSensitiveExceptionMessage() {
        PaymentOutboxEvent event = processingEvent();
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "owner-1"
        )).willReturn(Optional.of(event));

        service.markSendFailure(
            1L,
            "owner-1",
            new IllegalStateException("guest@example.com smtp-password raw provider payload")
        );

        assertThat(event.getLastError()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getLastError())
            .doesNotContain("guest@example.com", "smtp-password", "raw provider payload");
    }

    @Test
    void markSendFailure_maxRetriesMovesToFailed() {
        PaymentOutboxEvent event = PaymentOutboxEvent.builder()
            .id(1L)
            .eventType(com.min.edu.payment.outbox.domain.PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL)
            .aggregateId("ORDER-1")
            .payload("{}")
            .status(PaymentOutboxEventStatus.PROCESSING)
            .retryCount(5)
            .availableAt(OffsetDateTime.now())
            .leaseOwner("owner-1")
            .leaseUntil(OffsetDateTime.now().plusSeconds(30))
            .createdAt(OffsetDateTime.now())
            .build();
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "owner-1"
        )).willReturn(Optional.of(event));

        service.markSendFailure(1L, "owner-1", new IllegalStateException("smtp down"));

        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(6);
    }

    @Test
    void markSendFailure_staleOwnerCannotUpdate() {
        given(repository.findByIdAndStatusAndLeaseOwner(
            1L,
            PaymentOutboxEventStatus.PROCESSING,
            "stale-owner"
        )).willReturn(Optional.empty());

        assertThat(service.markSendFailure(1L, "stale-owner", new RuntimeException("late"))).isFalse();
    }

    private PaymentOutboxEvent processingEvent() {
        return processingEvent(0);
    }

    private PaymentOutboxEvent processingEvent(int retryCount) {
        return PaymentOutboxEvent.builder()
            .id(1L)
            .eventType(com.min.edu.payment.outbox.domain.PaymentOutboxEventType.SEND_TICKET_RESERVATION_CONFIRMATION_EMAIL)
            .aggregateId("ORDER-1")
            .payload("{}")
            .status(PaymentOutboxEventStatus.PROCESSING)
            .retryCount(retryCount)
            .availableAt(OffsetDateTime.now())
            .leaseOwner("owner-1")
            .leaseUntil(OffsetDateTime.now().plusSeconds(30))
            .createdAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOutboxProperties properties() {
        PaymentOutboxProperties properties = new PaymentOutboxProperties();
        properties.setInitialBackoff(Duration.ofSeconds(5));
        properties.setMaxBackoff(Duration.ofMinutes(5));
        properties.setMaxRetries(5);
        return properties;
    }
}
