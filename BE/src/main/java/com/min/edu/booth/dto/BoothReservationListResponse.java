package com.min.edu.booth.dto;

import com.min.edu.booth.domain.BoothReservationStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 내 부스 예약 목록 화면용 - 부스/행사/시간대 정보를 함께 내려준다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReservationListResponse {

    private Long id;
    private Long boothId;
    private String boothCode;
    private String boothDisplayName;
    private Long eventId;
    private String eventName;
    private Long slotId;
    private OffsetDateTime slotStartAt;
    private OffsetDateTime slotEndAt;
    private Integer partySize;
    private BoothReservationStatus status;
    private OffsetDateTime reservedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime checkedInAt;
    private OffsetDateTime noShowAt;
}
