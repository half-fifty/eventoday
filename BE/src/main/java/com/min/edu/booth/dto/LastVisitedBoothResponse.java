package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LastVisitedBoothResponse {
    private Long reservationId;
    private Long boothId;
    private Long memberId;
    private OffsetDateTime checkedInAt;
    private Integer partySize;
}