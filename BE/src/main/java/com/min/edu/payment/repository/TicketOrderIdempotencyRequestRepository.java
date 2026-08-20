package com.min.edu.payment.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;

import jakarta.persistence.LockModeType;

public interface TicketOrderIdempotencyRequestRepository
        extends JpaRepository<TicketOrderIdempotencyRequest, Long> {

    Optional<TicketOrderIdempotencyRequest> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
        insert into ticket_order_idempotency_requests (
            idempotency_key,
            request_hash,
            status,
            event_id,
            created_at,
            updated_at,
            expires_at
        )
        values (
            :idempotencyKey,
            :requestHash,
            'PROCESSING',
            :eventId,
            :now,
            :now,
            :expiresAt
        )
        on conflict (idempotency_key) do nothing
        """, nativeQuery = true)
    int insertProcessingIfAbsent(
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("eventId") Long eventId,
        @Param("now") OffsetDateTime now,
        @Param("expiresAt") OffsetDateTime expiresAt
    );

    @Modifying
    @Query(value = """
        insert into ticket_order_idempotency_requests (
            idempotency_key,
            request_hash,
            status,
            event_id,
            created_at,
            updated_at
        )
        values (
            :idempotencyKey,
            :requestHash,
            'FAILED',
            :eventId,
            :now,
            :now
        )
        on conflict (idempotency_key) do nothing
        """, nativeQuery = true)
    int insertFailedIfAbsent(
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("eventId") Long eventId,
        @Param("now") OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM TicketOrderIdempotencyRequest r
        WHERE r.idempotencyKey = :idempotencyKey
        """)
    Optional<TicketOrderIdempotencyRequest> findByIdempotencyKeyForUpdate(
        @Param("idempotencyKey") String idempotencyKey
    );
}
