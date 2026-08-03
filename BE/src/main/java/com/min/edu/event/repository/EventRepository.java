package com.min.edu.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.event.domain.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
}
