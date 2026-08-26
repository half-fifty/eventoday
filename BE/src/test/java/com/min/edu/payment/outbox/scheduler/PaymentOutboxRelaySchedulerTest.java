package com.min.edu.payment.outbox.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import com.min.edu.payment.config.PaymentOutboxProperties;
import com.min.edu.payment.outbox.repository.PaymentOutboxEventRepository;
import com.min.edu.payment.outbox.service.PaymentOutboxPublishRunner;

class PaymentOutboxRelaySchedulerTest {

    @Test
    void relay_delegatesClaimableIdsToRunner() {
        PaymentOutboxEventRepository repository = Mockito.mock(PaymentOutboxEventRepository.class);
        PaymentOutboxPublishRunner runner = Mockito.mock(PaymentOutboxPublishRunner.class);
        PaymentOutboxProperties properties = new PaymentOutboxProperties();
        properties.setBatchSize(2);
        properties.setFixedDelay(Duration.ofSeconds(5));
        PaymentOutboxRelayScheduler scheduler =
            new PaymentOutboxRelayScheduler(repository, runner, properties);
        given(repository.findClaimableIds(any(), any(Pageable.class)))
            .willReturn(List.of(1L, 2L));

        scheduler.relay();

        verify(runner).publish(eq(1L), any());
        verify(runner).publish(eq(2L), any());
    }

    @Test
    void relay_noCandidatesDoesNothing() {
        PaymentOutboxEventRepository repository = Mockito.mock(PaymentOutboxEventRepository.class);
        PaymentOutboxPublishRunner runner = Mockito.mock(PaymentOutboxPublishRunner.class);
        PaymentOutboxRelayScheduler scheduler =
            new PaymentOutboxRelayScheduler(repository, runner, new PaymentOutboxProperties());
        given(repository.findClaimableIds(any(), any(Pageable.class))).willReturn(List.of());

        scheduler.relay();

        verify(runner, never()).publish(any(), any());
    }
}
