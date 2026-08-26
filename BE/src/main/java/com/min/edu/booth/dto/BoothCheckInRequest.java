package com.min.edu.booth.dto;

import jakarta.validation.constraints.NotBlank;
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
    @NotNull(message = "입장권 정보는 필수입니다")
    private Long admissionTicketId;

    // 부스 현장에 게시된 QR(booth-detail 페이지 링크에 담긴 booth.qrToken)에서 읽어온 값.
    // 서버가 이 값을 해당 부스의 발급된 토큰과 대조하므로, 실제로 그 QR을 스캔하지 않고
    // boothId만 알아서 API를 직접 호출하는 방식으로는 체크인할 수 없다.
    @NotBlank(message = "QR 정보는 필수입니다")
    private String qrToken;
}
