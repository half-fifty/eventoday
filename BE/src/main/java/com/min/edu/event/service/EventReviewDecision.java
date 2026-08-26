package com.min.edu.event.service;
public record EventReviewDecision(Long eventId, Long organizationId, String eventName, boolean approved, String reason) {}
