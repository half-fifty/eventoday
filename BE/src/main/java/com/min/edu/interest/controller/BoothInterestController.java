package com.min.edu.interest.controller;

import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.service.BoothInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @RequestParam Long memberId) {
        boothInterestService.register(memberId, boothId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{boothId}/interests")
    public ResponseEntity<Void> remove(
            @PathVariable Long boothId,
            @RequestParam Long memberId) {
        boothInterestService.remove(memberId, boothId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/interests")
    public ResponseEntity<List<InterestBoothResponse>> getMyInterests(
            @RequestParam Long memberId) {
        return ResponseEntity.ok(boothInterestService.getMyInterests(memberId));
    }
}