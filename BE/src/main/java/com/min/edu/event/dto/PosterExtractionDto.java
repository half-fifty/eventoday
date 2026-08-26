package com.min.edu.event.dto;

import java.util.List;

public record PosterExtractionDto(
        String eventName,
        String startDate,
        String endDate,
        String venueName,
        String hall,
        String officialWebsiteUrl,
        String organizer,
        String operator,
        List<String> sponsors,
        String summary,
        String rawVisibleText,
        List<String> categoryCodes,
        List<String> warnings) {
}
