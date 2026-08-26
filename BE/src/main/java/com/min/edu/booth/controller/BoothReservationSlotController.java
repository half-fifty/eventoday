package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.dto.UpdateBoothReservationSlotRequest;
import com.min.edu.booth.service.BoothReservationSlotService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothReservationSlotController {

    private final BoothReservationSlotService boothReservationSlotService;

    // 부스 예약 시간대 목록 (공개) - 방문객 예약 화면, 운영자 슬롯 관리 화면에서 공용으로 사용
    @GetMapping("/{boothId}/reservation-slots")
    public ResponseEntity<List<BoothReservationSlotResponse>> listReservationSlots(
            @PathVariable Long boothId) {
        return ResponseEntity.ok(boothReservationSlotService.listSlots(boothId));
    }

    @PostMapping("/{boothId}/reservation-slots")
    public ResponseEntity<BoothReservationSlotResponse> createReservationSlot(
            @PathVariable Long boothId,
            @Valid @RequestBody CreateBoothReservationSlotRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationSlotResponse response =
                boothReservationSlotService.createReservationSlot(boothId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{boothId}/reservation-slots/{slotId}")
    public ResponseEntity<BoothReservationSlotResponse> updateReservationSlot(
            @PathVariable Long boothId,
            @PathVariable Long slotId,
            @Valid @RequestBody UpdateBoothReservationSlotRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationSlotResponse response =
                boothReservationSlotService.updateReservationSlot(boothId, slotId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{boothId}/reservation-slots/{slotId}/close")
    public ResponseEntity<BoothReservationSlotResponse> closeReservationSlot(
            @PathVariable Long boothId,
            @PathVariable Long slotId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationSlotResponse response =
                boothReservationSlotService.closeReservationSlot(boothId, slotId, principal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{boothId}/reservation-slots/{slotId}/open")
    public ResponseEntity<BoothReservationSlotResponse> reopenReservationSlot(
            @PathVariable Long boothId,
            @PathVariable Long slotId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationSlotResponse response =
                boothReservationSlotService.reopenReservationSlot(boothId, slotId, principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{boothId}/reservation-slots/{slotId}")
    public ResponseEntity<Void> deleteReservationSlot(
            @PathVariable Long boothId,
            @PathVariable Long slotId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        boothReservationSlotService.deleteReservationSlot(boothId, slotId, principal);
        return ResponseEntity.noContent().build();
    }
}