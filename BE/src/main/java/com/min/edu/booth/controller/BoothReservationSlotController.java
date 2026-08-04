package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.service.BoothReservationSlotService;
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
            @RequestBody CreateBoothReservationSlotRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        // TODO: principal의 memberId로 이 부스를 소유한 조직인지 검증
        // (지금은 스킵, 부스 담당 팀원과 협의 후 추가)

        BoothReservationSlotResponse response = boothReservationSlotService.createReservationSlot(boothId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}