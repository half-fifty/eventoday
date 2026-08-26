package com.min.edu.booth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBoothReservationRequest {

    @JsonProperty("slotId")
    @NotNull(message = "slotId is required")
    private Long slotId;

    @JsonProperty("partySize")
    @NotNull(message = "partySize is required")
    @Positive(message = "partySize must be positive")
    private Integer partySize;
}