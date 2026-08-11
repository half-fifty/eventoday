package com.min.edu.booth.controller;

import com.min.edu.booth.dto.RecommendedBoothsResponse;
import com.min.edu.booth.service.BoothRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}")
public class BoothRecommendationController {

    private final BoothRecommendationService recommendationService;

    /**
     * WBS-163: 혼잡도 기반 부스 추천
     * 현재 혼잡한 부스들을 파악하고 한산한 부스를 추천
     */
    @GetMapping("/recommended-booths")
    public ResponseEntity<RecommendedBoothsResponse> getRecommendedBooths(
            @PathVariable Long eventId,
            Pageable pageable) {

        RecommendedBoothsResponse response = recommendationService.getRecommendedBooths(eventId, pageable);
        return ResponseEntity.ok(response);
    }
}