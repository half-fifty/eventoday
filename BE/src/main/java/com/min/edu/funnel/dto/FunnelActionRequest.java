package com.min.edu.funnel.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.min.edu.funnel.domain.FunnelActionType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FunnelActionRequest(
        @NotNull
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String sessionId,

        @NotNull
        Long eventId,

        @NotNull
        FunnelActionType actionType,

        @NotNull
        OffsetDateTime occurredAt,

        Map<String, Object> properties) {
}
