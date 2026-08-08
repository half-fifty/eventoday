package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothAverageRatingResponse;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.service.BoothReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothReviewController {

    private final BoothReviewService boothReviewService;

    // 리뷰 작성
    @PostMapping("/{boothId}/reviews")
    public ResponseEntity<BoothReviewResponse> createReview(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewResponse response = boothReviewService.createReview(
                boothId, request, principal.getMemberId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 평균 별점 조회
    @GetMapping("/{boothId}/average-rating")
    public ResponseEntity<BoothAverageRatingResponse> getAverageRating(
            @PathVariable Long boothId) {

        BoothAverageRatingResponse response = boothReviewService.getAverageRating(boothId);
        return ResponseEntity.ok(response);
    }

    // WBS-159: 부스별 후기 목록 조회
    @GetMapping("/{boothId}/reviews")
    public ResponseEntity<Page<BoothReviewResponse>> getBoothReviews(
            @PathVariable Long boothId,
            Pageable pageable) {

        Page<BoothReviewResponse> response = boothReviewService.getBoothReviews(boothId, pageable);
        return ResponseEntity.ok(response);
    }

    // WBS-160: 내 작성 후기 목록 조회
    @GetMapping("/reviews/my")
    public ResponseEntity<Page<BoothReviewResponse>> getMyReviews(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            Pageable pageable) {

        Page<BoothReviewResponse> response = boothReviewService.getMyReviews(
                principal.getMemberId(), pageable
        );
        return ResponseEntity.ok(response);
    }
}