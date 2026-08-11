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
public class BoothReservationAdminResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("slotId")
    private Long slotId;

    @JsonProperty("startAt")
    private OffsetDateTime startAt;

    @JsonProperty("endAt")
    private OffsetDateTime endAt;

    @JsonProperty("memberId")
    private Long memberId;

    @JsonProperty("memberNickname")
    private String memberNickname;

    @JsonProperty("memberEmail")
    private String memberEmail;

    @JsonProperty("partySize")
    private Integer partySize;

    @JsonProperty("status")
    private BoothReservationStatus status;

    @JsonProperty("reservedAt")
    private OffsetDateTime reservedAt;

    @JsonProperty("cancelledAt")
    private OffsetDateTime cancelledAt;
}
