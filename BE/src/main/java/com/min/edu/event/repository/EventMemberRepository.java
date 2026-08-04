package com.min.edu.event.repository;

import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMemberRepository extends JpaRepository<EventMember, Long> {
    List<EventMember> findAllByEventIdOrderByCreatedAtAsc(Long eventId);
    Optional<EventMember> findByEventIdAndMemberId(Long eventId, Long memberId);
    boolean existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            Long eventId, Long memberId, EventRole eventRole);
}
