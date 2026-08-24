package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothCheckInRequest;
import com.min.edu.booth.dto.BoothCheckInResponse;
import com.min.edu.booth.service.BoothCheckInService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths/{boothId}")
public class BoothCheckInController {

    private final BoothCheckInService checkInService;

    /**
     * 부스 체크인 (입장 QR 인식)
     * - 게이트에서 이미 입장 처리된 본인의 AdmissionTicket으로 방문객을 식별
     * - 부스당 한 번만 체크인 가능 (booth_qr_scans 유니크 제약)
     * - BoothQrScan 기록 저장 (혼잡도 계산용) + 슬롯 예약이 있으면 CHECKED_IN으로 함께 반영
     */
    @PostMapping("/check-in")
    public ResponseEntity<BoothCheckInResponse> checkIn(
            @PathVariable Long boothId,
            @RequestBody @Valid BoothCheckInRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // 1) 인증 검증
        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        BoothCheckInResponse response = checkInService.checkIn(
                boothId,
                request.getAdmissionTicketId(),
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }
}
