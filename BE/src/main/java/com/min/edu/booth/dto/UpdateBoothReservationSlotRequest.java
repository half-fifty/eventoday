package com.min.edu.booth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBoothReservationSlotRequest {

    @JsonProperty("startAt")
    @NotNull(message = "startAt is required")
    private OffsetDateTime startAt;

    @JsonProperty("endAt")
    @NotNull(message = "endAt is required")
    private OffsetDateTime endAt;

    @JsonProperty("capacity")
    @NotNull(message = "capacity is required")
    @Positive(message = "capacity must be positive")
    private Integer capacity;
}