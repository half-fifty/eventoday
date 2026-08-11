package com.min.edu.booth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkReservationAttendanceRequest {

    // true: 방문 확인(CHECKED_IN), false: 노쇼 처리(NO_SHOW)
    @JsonProperty("attended")
    @NotNull(message = "attended is required")
    private Boolean attended;
}
