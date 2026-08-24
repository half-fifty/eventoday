package com.min.edu.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class EventContentSuggestionDto {
    private EventContentSuggestionDto() {}
    public record Request(@NotNull Long organizationId, @Size(max = 200) String eventName,
            @Size(max = 30) String eventType, String startAt, String endAt,
            @Size(max = 200) String venueName, @Size(max = 500) String posterSummary,
            @Size(max = 4000) String posterVisibleText, List<String> allowedCategoryCodes) {}
    public record Response(String shortDescription, String description,
            List<String> categoryCodes, List<String> sources, List<String> warnings,
            String notice) {}
}
