package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.service.BoothReservationService;
import com.min.edu.booth.service.RedisReservationService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothReservationController {

    private final BoothReservationService boothReservationService;
    private final RedisReservationService redisReservationService;  // ← 추가

    @PostMapping("/{boothId}/reservations")
    public ResponseEntity<BoothReservationResponse> createReservation(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReservationRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // WBS-146: Redis 선점
        boolean reserved = redisReservationService.reserveSlot(
                boothId, request.getSlotId(), principal.getMemberId());
        if (!reserved) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            BoothReservationResponse response = boothReservationService.createReservation(
                    boothId, request, principal.getMemberId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // 실패 시 Redis 해제
            redisReservationService.releaseSlot(boothId, request.getSlotId());
            throw e;
        }
    }

    @DeleteMapping("/{boothId}/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long boothId,
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        boothReservationService.cancelReservation(reservationId, boothId, principal.getMemberId());

        // WBS-146: Redis 선점 해제
        // (cancelReservation 내부에서 처리해도 됨)

        return ResponseEntity.noContent().build();
    }
}