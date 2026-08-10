package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothCheckInRequest;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.service.BoothCheckInService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
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
     * 부스 방문 확인 (checkIn)
     * - 교환 코드로 입장 확인
     * - 예약 상태 변경: RESERVED → CHECKED_IN
     * - BoothQrScan 기록 저장
     */
    @PostMapping("/check-in")
    public ResponseEntity<BoothReservationResponse> checkIn(
            @PathVariable Long boothId,
            @RequestBody BoothCheckInRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // 1) 인증 검증
        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }


        BoothReservationResponse response = checkInService.checkIn(
                boothId,
                request.getExchangeCode(),
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }
}
