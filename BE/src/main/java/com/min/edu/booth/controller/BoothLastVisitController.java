package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.LastVisitedBoothResponse;
import com.min.edu.booth.service.BoothLastVisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothLastVisitController {

    private final BoothLastVisitService boothLastVisitService;

    @GetMapping("/last-visited")
    public ResponseEntity<LastVisitedBoothResponse> getLastVisitedBooth(
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        LastVisitedBoothResponse response = boothLastVisitService.getLastVisitedBooth(principal.getMemberId());
        return ResponseEntity.ok(response);
    }
}