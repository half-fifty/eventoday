package com.min.edu.notification.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
        OutboxEventStatus status,
        OffsetDateTime now
    );

    @Modifying
    @Query(
        "delete from OutboxEvent e where e.status = :status and e.publishedAt < :threshold"
    )
    int deleteByStatusAndPublishedAtBefore(
        @Param("status") OutboxEventStatus status,
        @Param("threshold") OffsetDateTime threshold
    );
}
