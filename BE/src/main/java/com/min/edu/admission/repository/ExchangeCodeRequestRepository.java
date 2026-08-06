package com.min.edu.admission.repository;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeCodeRequestRepository extends JpaRepository<ExchangeCodeRequest, Long> {

    boolean existsByEventIdAndStatus(Long eventId, ExchangeCodeRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExchangeCodeRequest r where r.id = :id")
    Optional<ExchangeCodeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query(
        value = """
            select
                r.id as requestId,
                r.eventId as eventId,
                e.name as eventName,
                r.requestedBy as requestedBy,
                m.nickname as requesterNickname,
                r.requestedQuantity as requestedQuantity,
                r.purpose as purpose,
                r.status as status,
                r.reviewedBy as reviewedBy,
                r.reviewedAt as reviewedAt,
                r.rejectionReason as rejectionReason,
                r.emailedAt as emailedAt,
                r.createdAt as createdAt
            from ExchangeCodeRequest r
            join Event e on e.id = r.eventId
            join Member m on m.id = r.requestedBy
            where r.eventId = :eventId
                and (:status is null or r.status = :status)
            order by r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from ExchangeCodeRequest r
            join Event e on e.id = r.eventId
            join Member m on m.id = r.requestedBy
            where r.eventId = :eventId
                and (:status is null or r.status = :status)
            """
    )
    Page<ExchangeCodeRequestView> findEventRequests(
            @Param("eventId") Long eventId,
            @Param("status") ExchangeCodeRequestStatus status,
            Pageable pageable);

    @Query(
        value = """
            select
                r.id as requestId,
                r.eventId as eventId,
                e.name as eventName,
                r.requestedBy as requestedBy,
                m.nickname as requesterNickname,
                r.requestedQuantity as requestedQuantity,
                r.purpose as purpose,
                r.status as status,
                r.reviewedBy as reviewedBy,
                r.reviewedAt as reviewedAt,
                r.rejectionReason as rejectionReason,
                r.emailedAt as emailedAt,
                r.createdAt as createdAt
            from ExchangeCodeRequest r
            join Event e on e.id = r.eventId
            join Member m on m.id = r.requestedBy
            where r.status = :status
            order by r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from ExchangeCodeRequest r
            join Event e on e.id = r.eventId
            join Member m on m.id = r.requestedBy
            where r.status = :status
            """
    )
    Page<ExchangeCodeRequestView> findAdminRequests(
            @Param("status") ExchangeCodeRequestStatus status,
            Pageable pageable);

    @Query("""
        select
            r.id as requestId,
            r.eventId as eventId,
            e.name as eventName,
            r.requestedBy as requestedBy,
            m.nickname as requesterNickname,
            r.requestedQuantity as requestedQuantity,
            r.purpose as purpose,
            r.status as status,
            r.reviewedBy as reviewedBy,
            r.reviewedAt as reviewedAt,
            r.rejectionReason as rejectionReason,
            r.emailedAt as emailedAt,
            r.createdAt as createdAt
        from ExchangeCodeRequest r
        join Event e on e.id = r.eventId
        join Member m on m.id = r.requestedBy
        where r.id = :requestId
        """)
    Optional<ExchangeCodeRequestView> findRequestDetail(@Param("requestId") Long requestId);
}
