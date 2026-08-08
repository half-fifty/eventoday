package com.min.edu.admission.repository;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionLog;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.dto.AdmissionLogView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionLogRepository extends JpaRepository<AdmissionLog, Long> {

    @Query(
        value = """
            select
                l.id as admissionLogId,
                l.admissionTicketId as admissionTicketId,
                l.action as action,
                l.result as result,
                l.gateName as gateName,
                staff.nickname as staffNickname,
                l.processedAt as processedAt
            from AdmissionLog l
            join AdmissionTicket t on t.id = l.admissionTicketId
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            join Member staff on staff.id = l.staffMemberId
            where e.id = :eventId
                and (:action is null or l.action = :action)
                and (:result is null or l.result = :result)
            order by l.processedAt desc, l.id desc
            """,
        countQuery = """
            select count(l)
            from AdmissionLog l
            join AdmissionTicket t on t.id = l.admissionTicketId
            join ExchangeCode c on c.id = t.exchangeCodeId
            join Event e on e.id = c.eventId
            join Member staff on staff.id = l.staffMemberId
            where e.id = :eventId
                and (:action is null or l.action = :action)
                and (:result is null or l.result = :result)
            """
    )
    Page<AdmissionLogView> findEventAdmissionLogs(
            @Param("eventId") Long eventId,
            @Param("action") AdmissionAction action,
            @Param("result") AdmissionResult result,
            Pageable pageable);
}
