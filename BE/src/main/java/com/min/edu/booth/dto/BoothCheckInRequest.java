package com.min.edu.booth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothCheckInRequest {

    // 본인의 AdmissionTicket id — 게이트에서 이미 입장 처리(USED)된 입장권이어야 한다.
    // 방문객은 부스 현장의 QR(부스 상세 페이지로 랜딩)을 스캔해 이 화면에 도달하므로,
    // 여기서 다시 QR 문자열을 입력받을 필요 없이 로그인한 본인의 티켓 id만 넘기면 된다.
    @NotNull(message = "입장권 정보는 필수입니다")
    private Long admissionTicketId;
}
