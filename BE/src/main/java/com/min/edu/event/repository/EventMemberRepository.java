package com.min.edu.event.repository;

import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventMemberRepository extends JpaRepository<EventMember, Long> {
    List<EventMember> findAllByEventIdOrderByCreatedAtAsc(Long eventId);
    Optional<EventMember> findByEventIdAndMemberId(Long eventId, Long memberId);
    boolean existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            Long eventId, Long memberId, EventRole eventRole);

    @Query("""
            select member.eventId
            from EventMember member
            where member.memberId = :memberId
              and member.active = true
            """)
    List<Long> findActiveEventIdsByMemberId(@Param("memberId") Long memberId);

    @Query("""
            select e.id as eventId, e.name as eventName, e.startAt as startAt, e.endAt as endAt,
                   member.eventRole as role
            from EventMember member
            join Event e on e.id = member.eventId
            where member.memberId = :memberId
              and member.active = true
              and member.eventRole in :roles
            order by e.startAt asc, e.id asc
            """)
    List<AdmissionEventProjection> findAdmissionEventsByMemberId(
            @Param("memberId") Long memberId,
            @Param("roles") Collection<EventRole> roles);
}
