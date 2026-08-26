package com.min.edu.admission.repository;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.dto.AdmissionTicketView;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionTicketRepository extends JpaRepository<AdmissionTicket, Long> {

    boolean existsByQrToken(String qrToken);

    boolean existsByExchangeCodeId(Long exchangeCodeId);

    Optional<AdmissionTicket> findByExchangeCodeId(Long exchangeCodeId);

    // 부스 후기 작성/예약 권한 확인용: 이 회원이 이 행사의 "유효한" 입장권을 갖고 있는지 확인.
    // CANCELLED(환불)·EXPIRED 상태는 제외한다 — 환불받은 사람이 여전히 "티켓 구매자"로 인정되면 안 되기 때문.
    @Query("""
        select case when count(t) > 0 then true else false end
        from AdmissionTicket t
        join ExchangeCode c on c.id = t.exchangeCodeId
        where t.memberId = :memberId and c.eventId = :eventId
          and t.status in (com.min.edu.admission.domain.AdmissionTicketStatus.ISSUED,
                            com.min.edu.admission.domain.AdmissionTicketStatus.USED)
        """)
    boolean existsByMemberIdAndEventId(@Param("memberId") Long memberId, @Param("eventId") Long eventId);

    Optional<AdmissionTicket> findByQrToken(String qrToken);

    @Query("""
        select t
        from AdmissionTicket t
        join ExchangeCode c on c.id = t.exchangeCodeId
        where t.id = :admissionTicketId
            and c.eventId = :eventId
        """)
    Optional<AdmissionTicket> findByIdAndEventId(
            @Param("admissionTicketId") Long admissionTicketId,
            @Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AdmissionTicket t where t.qrToken = :qrToken")
    Optional<AdmissionTicket> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AdmissionTicket t where t.id = :id")
    Optional<AdmissionTicket> findByIdForUpdate(@Param("id") Long id);

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
        where c.ticketOrderId = :ticketOrderId
        order by t.issuedAt desc, t.id desc
        """)
    List<AdmissionTicketView> findGuestOrderAdmissionTickets(
            @Param("ticketOrderId") Long ticketOrderId);

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
            and c.ticketOrderId = :ticketOrderId
        """)
    Optional<AdmissionTicketView> findGuestOrderAdmissionTicketDetail(
            @Param("ticketOrderId") Long ticketOrderId,
            @Param("admissionTicketId") Long admissionTicketId);

    @Query("""
        select t
        from AdmissionTicket t
        join ExchangeCode c on c.id = t.exchangeCodeId
        where t.id = :admissionTicketId
            and c.ticketOrderId = :ticketOrderId
        """)
    Optional<AdmissionTicket> findByIdAndTicketOrderId(
            @Param("admissionTicketId") Long admissionTicketId,
            @Param("ticketOrderId") Long ticketOrderId);

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
