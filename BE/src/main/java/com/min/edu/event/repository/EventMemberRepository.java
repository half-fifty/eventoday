package com.min.edu.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;

public interface EventMemberRepository extends JpaRepository<EventMember, Long> {
    boolean existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
        Long eventId,
        Long memberId,
        EventRole eventRole
    );
}
