package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.service.BoothReservationSlotService;
import jakarta.validation.Valid;
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

    @PostMapping("/{boothId}/reservation-slots")
    public ResponseEntity<BoothReservationSlotResponse> createReservationSlot(
            @PathVariable Long boothId,
            @Valid @RequestBody CreateBoothReservationSlotRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationSlotResponse response =
                boothReservationSlotService.createReservationSlot(boothId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}