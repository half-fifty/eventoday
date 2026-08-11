package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationAdminResponse;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.dto.MarkReservationAttendanceRequest;
import com.min.edu.booth.service.BoothReservationWithRedisService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths/{boothId}/reservations")
public class BoothReservationController {

    private final BoothReservationWithRedisService reservationService;

    // 로그인한 회원의 이 부스에 대한 예약(있다면) 조회. 비로그인이면 null.
    @GetMapping
    public ResponseEntity<BoothReservationResponse> getMyReservation(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        if (principal == null) {
            return ResponseEntity.ok(null);
        }
        return ResponseEntity.ok(reservationService.getMyReservation(boothId, principal.getMemberId()));
    }

    // 운영자: 부스 예약자 전체 목록 (시간대별 예약 인원, 예약자 정보)
    @GetMapping("/admin")
    public ResponseEntity<List<BoothReservationAdminResponse>> listReservationsForManager(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        return ResponseEntity.ok(reservationService.listReservationsForManager(boothId, principal));
    }

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

    // 운영자: 예약자 출석 수동 체크 (attended=true 방문 확인, false 노쇼 처리)
    @PatchMapping("/{reservationId}/attendance")
    public ResponseEntity<BoothReservationAdminResponse> markAttendance(
            @PathVariable Long boothId,
            @PathVariable Long reservationId,
            @RequestBody @Valid MarkReservationAttendanceRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        return ResponseEntity.ok(reservationService.markAttendance(
                boothId, reservationId, request.getAttended(), principal));
    }

    /**
     * 예약 취소
     */
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