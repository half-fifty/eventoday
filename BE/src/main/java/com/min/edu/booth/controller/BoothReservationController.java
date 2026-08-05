package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.service.BoothReservationService;
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

    private final BoothReservationService reservationService;

    @PostMapping("/{boothId}/reservations")
    public ResponseEntity<BoothReservationResponse> createReservation(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReservationRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReservationResponse response = reservationService.createReservation(
                boothId, request, principal.getMemberId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{boothId}/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long boothId,
            @PathVariable Long reservationId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reservationService.cancelReservation(reservationId, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
