package com.min.edu.booth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.min.edu.booth.domain.BoothReservationStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReservationResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("boothId")
    private Long boothId;

    @JsonProperty("slotId")
    private Long slotId;

    @JsonProperty("memberId")
    private Long memberId;

    @JsonProperty("partySize")
    private Integer partySize;

    @JsonProperty("status")
    private BoothReservationStatus status;

    @JsonProperty("reservedAt")
    private OffsetDateTime reservedAt;

    @JsonProperty("cancelledAt")
    private OffsetDateTime cancelledAt;

    @JsonProperty("checkedInAt")
    private OffsetDateTime checkedInAt;

    @JsonProperty("noShowAt")
    private OffsetDateTime noShowAt;
}