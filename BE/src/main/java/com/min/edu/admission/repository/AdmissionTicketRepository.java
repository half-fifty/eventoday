package com.min.edu.admission.repository;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.dto.AdmissionTicketView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionTicketRepository extends JpaRepository<AdmissionTicket, Long> {

    boolean existsByQrToken(String qrToken);

    boolean existsByExchangeCodeId(Long exchangeCodeId);

    @Query(
        value = """
            select
                t.id as admissionTicketId,
                e.id as eventId,
                e.name as eventName,
                t.memberId as memberId,
                member.nickname as memberNickname,
                t.status as status,
                c.status as exchangeCodeStatus,
                t.issuedAt as issuedAt,
                t.usedAt as usedAt,
                t.cancelledAt as cancelledAt,
                t.qrToken as qrToken
            from AdmissionTicket t
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            left join Member member on member.id = t.memberId
            where t.memberId = :memberId
                and (:status is null or t.status = :status)
            order by t.issuedAt desc, t.id desc
            """,
        countQuery = """
            select count(t)
            from AdmissionTicket t
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            left join Member member on member.id = t.memberId
            where t.memberId = :memberId
                and (:status is null or t.status = :status)
            """
    )
    Page<AdmissionTicketView> findMyAdmissionTickets(
            @Param("memberId") Long memberId,
            @Param("status") AdmissionTicketStatus status,
            Pageable pageable);

    @Query("""
        select
            t.id as admissionTicketId,
            e.id as eventId,
            e.name as eventName,
            t.memberId as memberId,
            member.nickname as memberNickname,
            t.status as status,
            c.status as exchangeCodeStatus,
            t.issuedAt as issuedAt,
            t.usedAt as usedAt,
            t.cancelledAt as cancelledAt,
            t.qrToken as qrToken
        from AdmissionTicket t
        join ExchangeCode c on c.id = t.exchangeCodeId
        join Event e on e.id = c.eventId
        left join Member member on member.id = t.memberId
        where t.id = :admissionTicketId
        """)
    Optional<AdmissionTicketView> findAdmissionTicketDetail(
            @Param("admissionTicketId") Long admissionTicketId);

    @Query(
        value = """
            select
                t.id as admissionTicketId,
                e.id as eventId,
                e.name as eventName,
                t.memberId as memberId,
                member.nickname as memberNickname,
                t.status as status,
                c.status as exchangeCodeStatus,
                t.issuedAt as issuedAt,
                t.usedAt as usedAt,
                t.cancelledAt as cancelledAt,
                t.qrToken as qrToken
            from AdmissionTicket t
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            left join Member member on member.id = t.memberId
            where e.id = :eventId
                and (:status is null or t.status = :status)
            order by t.issuedAt desc, t.id desc
            """,
        countQuery = """
            select count(t)
            from AdmissionTicket t
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            left join Member member on member.id = t.memberId
            where e.id = :eventId
                and (:status is null or t.status = :status)
            """
    )
    Page<AdmissionTicketView> findEventAdmissionTickets(
            @Param("eventId") Long eventId,
            @Param("status") AdmissionTicketStatus status,
            Pageable pageable);
}
