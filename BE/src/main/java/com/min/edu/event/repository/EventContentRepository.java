package com.min.edu.event.repository;

import com.min.edu.event.domain.EventContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 공지·자료(event_contents) 리포지토리
 */
public interface EventContentRepository extends JpaRepository<EventContent, Long>,
        JpaSpecificationExecutor<EventContent> {
}