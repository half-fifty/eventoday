package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationListResponse;
import com.min.edu.booth.service.BoothReservationWithRedisService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/members/me/booth-reservations")
public class MemberBoothReservationController {

    private final BoothReservationWithRedisService reservationService;

    // 내 부스 예약 목록: 행사 전체에 걸쳐 회원이 예약한 모든 부스 예약을 최신순으로 조회
    @GetMapping
    public ResponseEntity<Page<BoothReservationListResponse>> listMyReservations(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @PageableDefault(size = 20) Pageable pageable) {

        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(reservationService.listMyReservations(principal.getMemberId(), pageable));
    }
}
