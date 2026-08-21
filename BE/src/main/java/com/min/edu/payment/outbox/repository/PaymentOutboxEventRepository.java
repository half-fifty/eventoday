package com.min.edu.payment.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.outbox.domain.PaymentOutboxEvent;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus;
import com.min.edu.payment.outbox.domain.PaymentOutboxEventType;

import jakarta.persistence.LockModeType;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    boolean existsByEventTypeAndAggregateId(
        PaymentOutboxEventType eventType,
        String aggregateId
    );

    @Query(
        "select e.id from PaymentOutboxEvent e where "
            + "(e.status = com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PENDING and e.availableAt <= :now) or "
            + "(e.status = com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PROCESSING and e.leaseUntil < :now) "
            + "order by e.availableAt asc, e.id asc"
    )
    List<Long> findClaimableIds(@Param("now") OffsetDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update PaymentOutboxEvent e set "
            + "e.status = com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PROCESSING, "
            + "e.leaseOwner = :leaseOwner, "
            + "e.leaseUntil = :leaseUntil "
            + "where e.id = :id and ("
            + "(e.status = com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PENDING and e.availableAt <= :now) or "
            + "(e.status = com.min.edu.payment.outbox.domain.PaymentOutboxEventStatus.PROCESSING and e.leaseUntil < :now))"
    )
    int claim(
        @Param("id") Long id,
        @Param("leaseOwner") String leaseOwner,
        @Param("now") OffsetDateTime now,
        @Param("leaseUntil") OffsetDateTime leaseUntil
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentOutboxEvent> findByIdAndStatusAndLeaseOwner(
        Long id,
        PaymentOutboxEventStatus status,
        String leaseOwner
    );
}
