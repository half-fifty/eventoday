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
public class BoothCheckInResponse {

    @JsonProperty("boothId")
    private Long boothId;

    @JsonProperty("memberId")
    private Long memberId;

    @JsonProperty("checkedInAt")
    private OffsetDateTime checkedInAt;

    // 이 부스에 슬롯 예약이 있어서 그 예약도 함께 CHECKED_IN으로 반영됐는지
    @JsonProperty("reservationLinked")
    private boolean reservationLinked;

    @JsonProperty("reservationStatus")
    private BoothReservationStatus reservationStatus;
}
