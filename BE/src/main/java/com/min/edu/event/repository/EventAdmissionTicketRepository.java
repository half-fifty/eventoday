package com.min.edu.event.repository;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지·자료 공개 대상(CONTENT-003) 판정 중 "관람객" 여부를 확인하기 위한 조회 전용 리포지토리.
 *
 * AdmissionTicket에는 eventId가 없고 교환 코드를 통해 행사와 연결되므로,
 * exchange_codes와 조인해 해당 행사의 입장권 보유 여부를 확인한다.
 */
public interface EventAdmissionTicketRepository extends JpaRepository<AdmissionTicket, Long> {

    /**
     * 회원이 해당 행사의 유효한 입장권을 보유하고 있는지.
     * 취소·만료 티켓은 관람객으로 보지 않으므로 statuses로 걸러 사용한다.
     */
    @Query("""
            select count(t) > 0
            from AdmissionTicket t, ExchangeCode c
            where t.exchangeCodeId = c.id
              and t.memberId = :memberId
              and c.eventId = :eventId
              and t.status in :statuses
            """)
    boolean existsEventAdmissionTicket(
            @Param("eventId") Long eventId,
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<AdmissionTicketStatus> statuses);
}
