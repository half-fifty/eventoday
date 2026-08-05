package com.min.edu.interest.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.service.BoothInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothInterestController {

    private final BoothInterestService boothInterestService;

    @PostMapping("/{boothId}/interests")
    public ResponseEntity<Void> register(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        boothInterestService.register(principal.getMemberId(), boothId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{boothId}/interests")
    public ResponseEntity<Void> remove(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        boothInterestService.remove(principal.getMemberId(), boothId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/interests")
    public ResponseEntity<List<InterestBoothResponse>> getMyInterests(
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        return ResponseEntity.ok(boothInterestService.getMyInterests(principal.getMemberId()));
    }
}