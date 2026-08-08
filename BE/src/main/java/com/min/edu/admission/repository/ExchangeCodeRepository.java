package com.min.edu.admission.repository;

import com.min.edu.admission.dto.ExchangeCodeView;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;

public interface ExchangeCodeRepository extends JpaRepository<ExchangeCode, Long> {

    boolean existsByCode(String code);

    Optional<ExchangeCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExchangeCode c where c.code = :code")
    Optional<ExchangeCode> findByCodeForUpdate(@Param("code") String code);

    List<ExchangeCode> findAllByTicketOrderIdOrderByIdAsc(Long ticketOrderId);

    List<ExchangeCode> findAllByExchangeCodeRequestIdOrderByIdAsc(Long exchangeCodeRequestId);

    long countByExchangeCodeRequestId(Long exchangeCodeRequestId);

    boolean existsByTicketOrderIdAndStatus(Long ticketOrderId, ExchangeCodeStatus status);

    @Query(
        value = """
            select
                c.id as exchangeCodeId,
                c.eventId as eventId,
                e.name as eventName,
                c.code as code,
                c.ticketOrderId as ticketOrderId,
                c.exchangeCodeRequestId as exchangeCodeRequestId,
                c.status as status,
                holder.nickname as holderNickname,
                c.expiresAt as expiresAt,
                c.redeemedAt as redeemedAt,
                c.createdAt as createdAt
            from ExchangeCode c
            join Event e on e.id = c.eventId
            left join Member holder on holder.id = c.holderMemberId
            where c.eventId = :eventId
                and (:status is null or c.status = :status)
            order by c.createdAt desc, c.id desc
            """,
        countQuery = """
            select count(c)
            from ExchangeCode c
            join Event e on e.id = c.eventId
            left join Member holder on holder.id = c.holderMemberId
            where c.eventId = :eventId
                and (:status is null or c.status = :status)
            """
    )
    Page<ExchangeCodeView> findEventExchangeCodes(
            @Param("eventId") Long eventId,
            @Param("status") ExchangeCodeStatus status,
            Pageable pageable);

    @Query(
        value = """
            select
                c.id as exchangeCodeId,
                c.eventId as eventId,
                e.name as eventName,
                c.code as code,
                c.ticketOrderId as ticketOrderId,
                c.exchangeCodeRequestId as exchangeCodeRequestId,
                c.status as status,
                holder.nickname as holderNickname,
                c.expiresAt as expiresAt,
                c.redeemedAt as redeemedAt,
                c.createdAt as createdAt
            from ExchangeCode c
            join Event e on e.id = c.eventId
            left join Member holder on holder.id = c.holderMemberId
            where c.holderMemberId = :memberId
                and (:status is null or c.status = :status)
            order by c.createdAt desc, c.id desc
            """,
        countQuery = """
            select count(c)
            from ExchangeCode c
            join Event e on e.id = c.eventId
            left join Member holder on holder.id = c.holderMemberId
            where c.holderMemberId = :memberId
                and (:status is null or c.status = :status)
            """
    )
    Page<ExchangeCodeView> findMyExchangeCodes(
            @Param("memberId") Long memberId,
            @Param("status") ExchangeCodeStatus status,
            Pageable pageable);
}
