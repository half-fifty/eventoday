package com.min.edu.booth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReservationSlotResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("boothId")
    private Long boothId;

    @JsonProperty("startAt")
    private OffsetDateTime startAt;

    @JsonProperty("endAt")
    private OffsetDateTime endAt;

    @JsonProperty("capacity")
    private Integer capacity;

    @JsonProperty("reservedCount")
    private Integer reservedCount;

    @JsonProperty("status")
    private BoothReservationSlotStatus status;
}