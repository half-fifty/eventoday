package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.service.BoothReservationWithRedisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
@RequiredArgsConstructor
@RequestMapping("/booths/{boothId}/reservations")
public class BoothReservationController {

    private final BoothReservationWithRedisService reservationService;

    /**
     * WBS-146: 예약 생성 (Redis 선점 통합)
     *
     * 모든 처리가 Service에서 통합됨:
     * - Redis 임시 선점
     * - DB 예약 저장
     * - Redis 선점 해제
     *
     * Controller는 Service 메서드만 호출
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<BoothReservationResponse> createReservation(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReservationRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // Service에서 Redis + DB를 통합 처리
        BoothReservationResponse response = reservationService.createReservationWithRedis(
                boothId,
                request,
                principal.getMemberId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 예약 취소
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long boothId,
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reservationService.cancelReservation(
                reservationId,
                boothId,
                principal.getMemberId()
        );
        return ResponseEntity.noContent().build();
    }
}