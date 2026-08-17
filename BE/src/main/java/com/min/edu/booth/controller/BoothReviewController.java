package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.BoothReviewSummaryResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.dto.UpdateBoothReviewRequest;
import com.min.edu.booth.service.BoothReviewService;
import com.min.edu.booth.service.BoothReviewSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths/{boothId}/reviews")
public class BoothReviewController {

    private final BoothReviewService reviewService;
    private final BoothReviewSummaryService reviewSummaryService;

    /**
     * 부스 후기 작성
     */
    @PostMapping
    public ResponseEntity<BoothReviewResponse> createReview(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewResponse response = reviewService.createReview(
                boothId,
                request,
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 부스 후기 수정
     */
    @PutMapping("/{reviewId}")
    public ResponseEntity<BoothReviewResponse> updateReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @RequestBody @Valid UpdateBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewResponse response = reviewService.updateReview(
                boothId,
                reviewId,
                request,
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 부스 후기 삭제
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reviewService.deleteReview(
                boothId,
                reviewId,
                principal.getMemberId()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * 부스의 모든 후기 조회 (페이징)
     */
    @GetMapping
    public ResponseEntity<Page<BoothReviewResponse>> getReviews(
            @PathVariable Long boothId,
            @PageableDefault(size = 10, page = 0)
            Pageable pageable) {

        Page<BoothReviewResponse> response = reviewService.getReviews(boothId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 부스의 평균 별점 조회
     */
    @GetMapping("/average-rating")
    public ResponseEntity<Double> getAverageRating(
            @PathVariable Long boothId) {

        Double averageRating = reviewService.getAverageRating(boothId);
        return ResponseEntity.ok(averageRating);
    }

    /**
     * 부스 리뷰 코멘트 AI 요약
     */
    @GetMapping("/summary")
    public ResponseEntity<BoothReviewSummaryResponse> getSummary(
            @PathVariable Long boothId) {

        BoothReviewSummaryResponse response = reviewSummaryService.getSummary(boothId);
        return ResponseEntity.ok(response);
    }
}