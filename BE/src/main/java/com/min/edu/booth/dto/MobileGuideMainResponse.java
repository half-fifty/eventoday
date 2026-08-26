package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MobileGuideMainResponse {

    private Long eventId;
    private String eventName;
    private OffsetDateTime eventStartDate;
    private OffsetDateTime eventEndDate;
    private String location;
    private Integer totalBooths;
    private List<MobileGuideBoothListResponse> booths;
}